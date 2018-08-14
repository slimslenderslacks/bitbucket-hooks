(ns atomist.gitlab
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [http.client :as http]
            [cljs.core.async :refer [<! >! chan close!] :as async]
            [cljs.pprint :refer [pprint]]
            [goog.string :as gstring]
            [goog.string.format]
            [atomist.cljs-log :as log]))

(comment
  (def system-hooks-api "api/v4/hooks")

  (def project-hooks-api "api/v4/projects/%s/hooks" project)
  (def project-hook-api "api/v4/projects/%s/hooks/%s" project hook-id) (http/get hooks-api {:headers {"PRIVATE_TOKEN" ""}}))

