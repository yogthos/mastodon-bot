#!/bin/bash
npx shadow-cljs release app
shasum -a 512 mastodon-bot.js > mastodon-bot.js.sha256
shasum -a 512 mastodon-bot.js > mastodon-bot.js.sha512
rm -rf target/npm-build/mastodon-bot
mkdir -p target/npm-build/mastodon-bot
cp mastodon-bot.js target/npm-build/mastodon-bot/
cp target/mastodon-bot.js.sha256 target/npm-build/mastodon-bot/
cp target/mastodon-bot.js.sha512 target/npm-build/mastodon-bot/
cp package.json target/npm-build/mastodon-bot/
cp README.md target/npm-build/mastodon-bot/
npm publish ./target/npm-build/mastodon-bot --access public
