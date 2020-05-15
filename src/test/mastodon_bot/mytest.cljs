(ns mastodon-bot.mytest
  (:require
   [mastodon-bot.infra :as infra]
   [clojure.pprint :refer [pprint]]
   [mastodon-bot.mastodon-api :as masto]))

(defn myconfig []
  (:mastodon-config (infra/load-config)))

(defn test-timeline []
  (masto/get-mastodon-timeline
   (myconfig)
   pprint))

(defn test-status []
  (masto/post-status
   (myconfig)
   "test"
   nil
   pprint))

(defn test-status-image []
  (masto/post-status-with-images
   (myconfig)
   "test2"
   ["https://pbs.twimg.com/media/ER3qX2RW4AEhQfW.jpg"]
   []
   pprint))
