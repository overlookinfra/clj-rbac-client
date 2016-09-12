(ns puppetlabs.rbac-client.services.rbac
  (:require
   [clojure.string]
   [clojure.tools.logging :as log]
   [puppetlabs.http.client.common :as http]
   [puppetlabs.http.client.sync :refer [create-client]]
   [puppetlabs.rbac-client.core :refer [json-api-caller]]
   [puppetlabs.rbac-client.protocols.rbac :refer [RbacConsumerService]]
   [puppetlabs.trapperkeeper.core :refer [defservice]]
   [puppetlabs.trapperkeeper.services :refer [service-context]]
   [slingshot.slingshot :refer [throw+]])
  (:import [java.util UUID]
           [java.net URL]))

(defn perm-str->map
  "Given a permission string of the form <object_type>:<action>:(<instance>|*),
  return a map that represents the permission.
  COPY-PASTE: https://github.com/puppetlabs/pe-rbac-service/blob/master/src/clj/puppetlabs/rbac/services/consumer.clj"
  [perm-str]
  (let [[object-type action instance] (clojure.string/split perm-str #":")]
    {:object_type object-type
     :action action
     :instance instance}))

(defn str->uuid
  "COPY-PASTE: https://github.com/puppetlabs/pe-rbac-service/blob/master/src/clj/puppetlabs/rbac/utils.clj"
  [str-uuid]
  (try
    (UUID/fromString str-uuid)
    (catch IllegalArgumentException e
      (throw+ {:uuid str-uuid
               ;; DOCS REVIEWED
               :msg (str "Error parsing UUID " str-uuid)
               :kind ::invalid-uuid}))))

(defn parse-subject
  [subject]
  (if subject
    (update subject :id str->uuid)))

(defn rbac-client
  "Wrap the json caller adding :throw-body to opts"
  ([client rbac-url method path] (rbac-client client rbac-url method path {}))
  ([client rbac-url method path opts]
   (json-api-caller client rbac-url method path (assoc opts :throw-body true))))

(defn api-url->status-url
  "Given an RBAC api-url, convert that to the related status URL"
  [^String api-url]
  (let [^URL api-url (java.net.URL. api-url)]
    (str (.getProtocol api-url)
         "://"
         (.getHost api-url)
         (when-not (= -1 (.getPort api-url))
           (str ":" (.getPort api-url)))
         "/status/v1/services")))

(defservice remote-rbac-consumer-service
  RbacConsumerService
  [[:ConfigService get-in-config]]

  (init [_ tk-ctx]
        (if-let [rbac-url (get-in-config [:rbac-consumer :api-url])]
          (let [client (create-client (get-in-config [:global :certs]))]
            (assoc tk-ctx
                   :client client
                   :rbac-client (partial rbac-client client rbac-url)
                   :status-client (partial rbac-client client (api-url->status-url rbac-url))))
          (throw+ {:kind :puppetlabs.rbac-client/invalid-configuration
                   :message "'rbac-consumer' not configured with an 'api-url'"})))

  (stop [this tk-ctx]
    (when-let [client (:client tk-ctx)]
      (http/close client))
    (dissoc tk-ctx :client))

  (is-permitted? [this subject perm-str]
                 (let [{:keys [rbac-client]} (service-context this)
                       body {:token (str (:id subject))
                             :permissions [(perm-str->map perm-str)]}]
                   (-> (rbac-client :post "/v1/permitted" {:body body})
                       :body
                       first)))

  (are-permitted? [this subject perm-strs]
                  (let [{:keys [rbac-client]} (service-context this)
                        body {:token (str (:id subject))
                              :permissions (map perm-str->map perm-strs)}]
                    (-> (rbac-client :post "/v1/permitted" {:body body})
                        :body)))

  (cert-whitelisted? [this ssl-client-cn]
                     (let [{:keys [rbac-client]} (service-context this)
                           url (str "/v1/certs/" ssl-client-cn)]
                       (-> (rbac-client :get url)
                           :body
                           :whitelisted)))

  (cert->subject [this ssl-client-cn]
                 (let [{:keys [rbac-client]} (service-context this)
                       url (str "/v1/certs/" ssl-client-cn)]
                   (-> (rbac-client :get url)
                       :body
                       :subject
                       (parse-subject))))

  (valid-token->subject [this jwt-str]
                        (let [{:keys [rbac-client]} (service-context this)
                              url (str "/v1/tokens/" jwt-str)]
                          (-> (rbac-client :get url)
                              :body
                              (parse-subject))))

  (status [this level]
          (let [{:keys [status-client]} (service-context this)]
            (-> (status-client :get "" {:query-params {"level" (name level)}})
                (get-in [:body :rbac-service])
                (update :state keyword)))))
