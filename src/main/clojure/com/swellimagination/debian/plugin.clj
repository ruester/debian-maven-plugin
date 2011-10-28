(ns com.swellimagination.debian.plugin)

;;; version separator character
;;; (used to separate the name from the version in a debian dependency property
(def vs "_")

(def mkdir   "mkdir")
(def fmt     "fmt")
(def debuild "debuild")
(def copy    "cp")
(def ls      "ls")
(def bash    "/bin/bash")


(def maintainer        "Oster Hase <osterhase@rapanui.com>")
(def section           "unknown")
(def priority          "optional")
(def build-depends     "debhelper (>= 7.0.50~)")
(def standards-version "3.9.1" )
(def homepage          "http://google.com")
(def architecture      "all")
(def description       "The Osterhase was too lazy to provide a description")
(def files             "*.jar")
(def target-subdir     "target")
(def install-dir       "/usr/share/java")

(defn- assoc-entry
  [m e]
  (assoc m (keyword (.getKey e)) (.getValue e)))

(defn java->map
  [object]
  (reduce assoc-entry {} object))
