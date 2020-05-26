#!/usr/bin/env lumo

(ns mastodon-bot.core_test
  (:require
   [cljs.test :refer-macros [deftest is testing run-tests]]
   [cljs.reader :as edn]
   ["fs" :as fs]
   [mastodon-bot.core :as core]
   ))

;; (deftest test-read-config
;;   (is (= 300 core/max-post-length)))

;; (defn readfile [filename]
;;   (-> filename (fs/readFileSync #js {:encoding "UTF-8"}) edn/read-string))

;; (deftest test-remove-link-to-image
;;   (is (=
;;          "Mensen vragen om meer foto's in SPAMSPAMSPAM, dus bij deze achteraf de nieuwe kasten voor de projectenkast en de bookcrossingzone. Te vinden direct bij binnenkomst op de eerste en tweede verdieping."
;;          (:text (core/parse-tweet (readfile "testdata/twitter/tweet-with-link-to-image.edn")))
;;          )))

;; (deftest test-parse-normal-tweet-text
;;   (is (=
;;          "Daar is 'ie dan! SPAMSPAMSPAM editie 2! Met een samenvatting van wat er in deze eerste twee maanden van 2020 gebeurd en gedaan is binnen @hack42. Lees het via: \nhttps://t.co/O1YzlWTFU3 #hackerspace #nieuws #arnhem #nuarnhem"
;;          (:text (core/parse-tweet (readfile "testdata/twitter/normal-tweet.edn")))
;;          )))

;; (deftest test-replacements
;;   (is (=
;;          "💠 Check out what has been going on during March in the world of @ReproBuilds! 💠 https://t.co/k6NsSO115z @opensuse@fosstodon.org @conservancy@mastodon.technology @PrototypeFund@mastodon.social @debian@fosstodon.org "
;;          (:text (core/perform-replacements (core/parse-tweet (readfile "testdata/twitter/tweet-mentions.edn"))))
;;          )))

(cljs.test/run-tests)

; Don't run core's -main when testing
(set! *main-cli-fn* ())
