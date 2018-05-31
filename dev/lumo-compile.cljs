(require '[lumo.build.api])
(lumo.build.api/build "src" {:main 'editors.core
                             :output-to "index.js"
                             :optimizations :none
                             :target :nodejs})
