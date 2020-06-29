FROM node:10-slim

RUN apt-get update && apt-get install --assume-yes software-properties-common && \
  apt-get install --assume-yes git cron

RUN npm install -g mastodon-bot

RUN mkdir /config && touch /config/config.edn && touch /var/log/cron.log

ADD poll.sh /poll.sh

ENV MASTODON_BOT_CONFIG /config/config.edn
VOLUME /config

CMD /poll.sh
