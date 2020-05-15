(ns mastodon-bot.infra
  (:require
   [cljs.reader :as edn]
   ["fs" :as fs]))

(defn js->edn [data]
  (js->clj data :keywordize-keys true))

(defn exit-with-error [error]
  (js/console.error error)
  (js/process.exit 1))

(defn find-config []
  (let [config (or (first *command-line-args*)
                   (-> js/process .-env .-MASTODON_BOT_CONFIG)
                   "config.edn")]
    (if (fs/existsSync config)
      config
      (exit-with-error (str "failed to read config: " config)))))

(defn load-config []
  (-> (find-config) (fs/readFileSync #js {:encoding "UTF-8"}) edn/read-string))