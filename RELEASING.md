# Releasing

## dev release
```
shadow-cljs compile test

shadow-cljs release app
rm -rf target/npm-build 
mkdir target/npm-build
cp mastodon-bot.js target/npm-build/
cp package.json target/npm-build/
cp README.md target/npm-build/
tar -c -C target/npm-build -f target/npm-build.tar .

npm publish ./target/npm-build.tar --access public --tag dev0
```

## prod release (should be done from master)
```
shadow-cljs compile test

#adjust version
vi shadow-cljs.edn

git commit -am "releasing"
git tag [version]
git push && git push --tag

shadow-cljs release app

shadow-cljs release app
rm -rf target/npm-build 
mkdir target/npm-build
cp mastodon-bot.js target/npm-build/
cp package.json target/npm-build/
cp README.md target/npm-build/
tar -c -C target/npm-build -f target/npm-build.tar .

npm publish ./target/npm-build.tar --access public --tag [version]

# Bump version
vi shadow-cljs.edn

git commit -am "version bump" && git push
```
