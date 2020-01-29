(defproject starnav "0.1"
  :min-lein-version "2.7.1"
  :dependencies [[org.clojure/clojure "1.9.0"]
                 [org.clojure/clojurescript "1.10.520"]]
  :source-paths ["src"]
  :plugins [[lein-cljsbuild "1.1.7"]]
  :aliases {"fig"       ["trampoline" "run" "-m" "figwheel.main"]
            "fig:build" ["trampoline" "run" "-m" "figwheel.main" "-b" "dev" "-r"]
            "fig:min"   ["run" "-m" "figwheel.main" "-O" "advanced" "-bo" "dev"]}
  :profiles {:dev {:dependencies [[com.bhauman/figwheel-main "0.2.3"]
                                  [com.bhauman/rebel-readline-cljs "0.1.4"]]}}
  :cljsbuild {:builds
              {:release {:source-paths ["src"]
                         :compiler {:optimizations :advanced
                                    :output-to "resources/public/cljs-out/dev-main.js"}}}})

