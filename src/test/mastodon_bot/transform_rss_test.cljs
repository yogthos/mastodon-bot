(ns mastodon-bot.transform-rss-test
  (:require
   [cljs.test :refer-macros [deftest is testing run-tests]]
   [clojure.spec.alpha :as s]
   [mastodon-bot.transform :as sut]
   ))

(def intermediate-rss-item {:created-at #inst "2020-06-26T12:17:33.000-00:00"
                            :text "Taking Theatre Online with WebGL and WebRTC\n\nhttps://chrisuehlinger.com/blog/2020/06/16/unshattering-the-audience-building-theatre-on-the-web-in-2020/"})

(deftest should-not-resolve-urls
  (is (= {:created-at #inst "2020-06-26T12:17:33.000-00:00"
          :text "Taking Theatre Online with WebGL and WebRTC\n\nhttps://chrisuehlinger.com/blog/2020/06/16/unshattering-the-audience-building-theatre-on-the-web-in-2020/"}
         (sut/intermediate-resolve-urls false intermediate-rss-item))))

(deftest should-not-append-screen-name
  (is (= {:created-at #inst "2020-06-26T12:17:33.000-00:00", 
          :text "Taking Theatre Online with WebGL and WebRTC

https://chrisuehlinger.com/blog/2020/06/16/unshattering-the-audience-building-theatre-on-the-web-in-2020/
#rssbot", 
          :reblogged true, :media-links nil}
         (sut/intermediate-to-mastodon {:target-type :mastodon
                                        :append-screen-name? false
                                        :max-post-length 500
                                        :signature "#rssbot"} 
                                       intermediate-rss-item)))
  (is (= {:created-at #inst "2020-06-26T12:17:33.000-00:00"
          :text "Taking Theatre Online with WebGL and WebRTC

https://chrisuehlinger.com/blog/2020/06/16/unshattering-the-audience-building-theatre-on-the-web-in-2020/
#rssbot"
          :reblogged true, :media-links nil}
         (sut/intermediate-to-mastodon {:target-type :mastodon
                                        :max-post-length 500
                                        :signature "#rssbot"}
                                       intermediate-rss-item))))

(deftest should-not-append-signature
  (is (= {:created-at #inst "2020-06-26T12:17:33.000-00:00"
          :text "Taking Theatre Online with WebGL and WebRTC

https://chrisuehlinger.com/blog/2020/06/16/unshattering-the-audience-building-theatre-on-the-web-in-2020/"
          :reblogged true, :media-links nil}
         (sut/intermediate-to-mastodon {:target-type :mastodon
                                        :append-screen-name? false
                                        :max-post-length 500}
                                       intermediate-rss-item)))
  )
