(ns com.swellimagination.debian.package-mojo
  (:require [clojure.string :as str]
            [clojure.contrib.duck-streams :as duck])
  (:use     [clojure.contrib.shell-out :only (sh)])
  (:import [java.util Date Properties Map Locale]
           [java.text SimpleDateFormat]
           [org.apache.maven.plugin MojoExecutionException]
           [org.apache.maven.project MavenProject])
  (:gen-class :name         com.swellimagination.debian.PackageMojo
              :extends      org.apache.maven.plugin.AbstractMojo
              :prefix       pm-
              :constructors {[org.apache.maven.project.MavenProject] []}
              :init         init
              :state        state
              :methods      [[build [java.util.Properties java.util.Map] void]]))

;;; version separator character
;;; (used to separate the name from the version in a debian dependency property
(def vs "_")

(def mkdir   "mkdir")
(def fmt     "fmt")
(def debuild "debuild")
(def copy    "cp")
(def ls      "ls")
(def bash    "/bin/bash")

(def default-maintainer        "Oster Hase <osterhase@rapanui.com>")
(def default-section           "unknown")
(def default-priority          "optional")
(def default-build-depends     "debhelper (>= 7.0.50~)")
(def default-standards-version "3.9.1" )
(def default-homepage          "http://localhost")
(def default-architecture      "all")
(def default-description       "<insert description here>")
(def default-files             "*.jar")
(def default-target-subdir     "target")
(def default-install-dir       "/usr/share/java")

(defn pm-init
  [project]
  [[] project])

(defn- get-override
  [overrides dependency-overrides dependency-name]
  (some (fn [re-str]
          (if-let [matches (re-matches (re-pattern re-str) dependency-name)]
            (str (.getProperty dependency-overrides re-str)
                 vs
                 (if (coll? matches) (second matches))))) overrides))

(defn- package-spec
  [package]
  (str (:name package)
       (if-let [version (:version package)]
         (str " (>= " version ")"))))

(defn- parse-spec
  [s]
  (zipmap [:name :version] (str/split s (re-pattern vs))))

(defn- format-dependencies
  [dependencies]
  (str/join ", " (conj (map #(package-spec %1) dependencies) "${misc:Depends}")))

(defn get-dependencies
  [this project overrides dependency-overrides]
  (for [dependency        (.getDependencies project)]
    (let [groupId         (.getGroupId dependency)
          artifactId      (.getArtifactId dependency)
          version         (.getVersion dependency)
          scope           (.getScope dependency)
          dependency-name (str groupId "." artifactId vs version)
          override        (get-override overrides dependency-overrides dependency-name)
          override-spec   (or override (str artifactId vs version))
          package         (if (not= scope "test") (parse-spec override-spec))]
      (if-not (empty? package)
              (.info (.getLog this) (str "Depends On " (package-spec package))))
      package)))

(defn- java->map
  [object]
  (reduce #(assoc %1 (keyword (.getKey %2)) (.getValue %2)) {} object))

(defn pm-build
  [this dependency-overrides configuration]
  (let [configuration        (java->map configuration)
        project              (.state this)
        artifactId           (:name configuration (.getArtifactId project))
        version              (.getVersion project)
        overrides            (enumeration-seq (.propertyNames dependency-overrides))
        dependencies         (get-dependencies this project overrides dependency-overrides)
        base-dir             (.getBasedir project)
        target-dir           (str/join "/" [base-dir (:targetDir configuration default-target-subdir )])
        package-dir          (str/join "/" [target-dir (str artifactId "-" version)])
        debian-dir           (str/join "/" [package-dir "debian"])
        install-dir          (:installDir configuration default-install-dir)]
    (sh mkdir "-p" debian-dir)
    (duck/write-lines
     (str/join "/" [debian-dir "control"])
     [(str "Source: "           artifactId)
      (str "Section: "           (:section configuration default-section))
      (str "Priority: "          (:priority configuration default-priority))
      (str "Maintainer: "        (:maintainer configuration default-maintainer))
      (str "Build-Depends: "     (:buildDepends configuration default-build-depends))
      (str "Standards-Version: " (:standardsVersion configuration default-standards-version))
      (str "Homepage: "          (:homepage configuration default-homepage))
      ""
      (str "Package: "           artifactId)
      (str "Architecture: "      (:architecture configuration default-architecture))
      (str "Depends: "           (format-dependencies dependencies))
      (str "Description: "       (sh fmt "-w60" :in (:description configuration default-description)))])
    (duck/write-lines
     (str/join "/" [debian-dir "changelog"])
     [(str artifactId " (" (:version configuration version) ") unstable; urgency=low")
      ""
      "  * Initial Release."
      ""
      (str " -- "
           (:maintainer configuration default-maintainer) "  "
           (.format (SimpleDateFormat. "EEE, d MMM yyyy HH:mm:ss Z" (Locale/CANADA)) (Date.)))])
    (duck/write-lines
     (str/join "/" [debian-dir "rules"])
     ["#!/usr/bin/make -f" "%:"
      "\tdh $@"])
    (duck/write-lines
     (str/join "/" [package-dir "Makefile"])
     [(str "INSTALLDIR := $(DESTDIR)/" install-dir)
      "build:"
      ""
      "install:"
      "\t@mkdir -p $(INSTALLDIR)"
      (str/join " "
                ["\t@cd" target-dir "&&"
                 copy (:files configuration default-files) "$(INSTALLDIR)"])])
    (.info (.getLog this) (sh debuild :dir debian-dir))))

