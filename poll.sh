#!/bin/sh

while true; do
  echo "Polling Bot"
  cd /mastodon-bot
  mastodon-bot
  echo "Poll done, waiting 600 seconds"
  sleep 600
done
