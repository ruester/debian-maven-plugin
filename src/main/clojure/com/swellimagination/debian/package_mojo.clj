(ns com.swellimagination.debian.package-mojo
  (:require [clojure.string :as str]
            [clojure.contrib.duck-streams :as duck])
  (:use     [clojure.contrib.shell-out :only (sh)])
  (:import [java.util Properties Map]
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

(def mkdir "/bin/mkdir")

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
         (str " (= " version ")"))))

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
          dependency-name (str groupId "." artifactId vs version)
          override        (get-override overrides dependency-overrides dependency-name)
          override-spec   (or override (str artifactId vs version))
          package         (parse-spec override-spec)]
      (.info (.getLog this) (str "Depends On " (package-spec package)))
      package)))

(defn pm-build
  [this dependency-overrides configuration]
  (let [project              (.state this)
        artifactId           (.getArtifactId project)
        version              (.getVersion project)
        overrides            (enumeration-seq (.propertyNames dependency-overrides))
        dependencies         (get-dependencies this project overrides dependency-overrides)
        base-dir             (.getBasedir project)
        debian-dir           (str/join "/" [base-dir
                                            (:target-dir configuration "target")
                                            (str artifactId "-" version)
                                            "debian"])
        target-dir           (str/join "/" [debian-dir artifactId])]
    (sh mkdir "-p" target-dir)
    (duck/write-lines
     (str/join "/" [debian-dir "control"])
     [(str "Source: "           artifactId)
      (str "Section: "          (:section configuration "unknown"))
      (str "Priority: "         (:priority configuration "optional"))
      (str "Maintainer: "       (:maintainer configuration "Oster Hase <osterhase@rapanui.com>"))
      (str "Build-Depends: "    (:buildDepends configuration "debhelper (>= 7.0.50~"))
      (str "Standards-Version:" (:standardsVersion configuration "3.9.1"))
      (str "Homepage:"          (:homepage configuration "http://localhost"))
      ""
      (str "Package:"           artifactId)
      (str "Architecture:"      (:architecture configuration "all"))
      (str "Version:"           (:version configuration version))
      (str "Depends:"           (format-dependencies dependencies))])))

