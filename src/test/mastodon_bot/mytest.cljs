(ns mastodon-bot.mytest
  (:require
   [mastodon-bot.infra :as infra]
   [clojure.pprint :refer [pprint]]   
   [mastodon-bot.twitter-api :as twitter]
   [mastodon-bot.core :as core]))

(defn myconfig []
  (core/mastodon-auth (infra/load-config)))

(defn run []
  (core/-main))
