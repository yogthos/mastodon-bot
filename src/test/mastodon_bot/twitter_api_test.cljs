(ns mastodon-bot.twitter-api-test
  (:require
   [cljs.test :refer-macros [deftest is testing run-tests]]
   [clojure.spec.alpha :as s]
   [cljs.reader :as edn]
   ["fs" :as fs]
   [mastodon-bot.twitter-api :as sut]
   ))

(defn readfile [filename]
  (-> filename (fs/readFileSync #js {:encoding "UTF-8"}) edn/read-string))

(def testconfig (readfile "test.edn"))

(deftest test-parse-normal-tweet-text
  (is (=
         "Daar is 'ie dan! SPAMSPAMSPAM editie 2! Met een samenvatting van wat er in deze eerste twee maanden van 2020 gebeurd en gedaan is binnen @hack42. Lees het via: \nhttps://t.co/O1YzlWTFU3 #hackerspace #nieuws #arnhem #nuarnhem"
         (:text (sut/parse-tweet (readfile "testdata/twitter/normal-tweet.edn")))
         )))

(deftest test-remove-link-to-image
  (is (=
    "Mensen vragen om meer foto's in SPAMSPAMSPAM, dus bij deze achteraf de nieuwe kasten voor de projectenkast en de bookcrossingzone. Te vinden direct bij binnenkomst op de eerste en tweede verdieping."
    (:text (sut/parse-tweet (readfile "testdata/twitter/tweet-with-link-to-image.edn")))
    )))
