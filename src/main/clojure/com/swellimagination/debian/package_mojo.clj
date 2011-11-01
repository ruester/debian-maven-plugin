(ns com.swellimagination.debian.package-mojo
  (:require [clojure.string :as str]
            [clojure.contrib.duck-streams :as duck])
  (:use     [clojure.contrib.shell-out :only (sh)]
            [com.swellimagination.debian.plugin])
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

(defn- format-description
  [configuration]
  (let [description (or (:description configuration) description)
        lines       (str/split-lines description)]
    (apply str
           (first lines) "\n"
           (map #(str " " (str/replace %1 #"\s+" " ") "\n") (rest lines)))))

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

(defn install-helper
  [debian-dir configuration script type cases]
  (if-let [commands (type configuration)]
    (duck/write-lines
     (path debian-dir script)
     ["#!/bin/sh"
      "set -e"
      "case \"$1\" in"
           (str cases ")")
           commands
      "    ;;"
      "esac"
      "#DEBHELPER#"
      "exit 0"
      ])))

(defn write-preinst
  [debian-dir configuration]
  (install-helper debian-dir configuration  "preinst" :preInstall "install|upgrade"))

(defn write-postinst
  [debian-dir config]
  (install-helper debian-dir config "postinst" :postInstall "configure"))

(defn write-prerm
  [debian-dir config]
  (install-helper debian-dir config "prerm" :preRemove "remove|upgrade|deconfigure"))

(defn write-postrm
  [debian-dir config]
  (install-helper
   debian-dir config "postrm" :postRemove
   "purge|remove|upgrade|failed-upgrade|abort-install|abort-upgrade|disappear"))

(defn pm-build
  [this dependency-overrides configuration]
  (let [configuration        (java->map configuration)
        project              (.state this)
        artifact-id          (:name configuration (.getArtifactId project))
        version              (.getVersion project)
        overrides            (enumeration-seq (.propertyNames dependency-overrides))
        dependencies         (get-dependencies this project overrides dependency-overrides)
        base-dir             (.getBasedir project)
        target-dir           (path base-dir (:targetDir configuration target-subdir ))
        package-dir          (path target-dir (str artifact-id "-" version))
        debian-dir           (path package-dir "debian")
        install-dir          (:installDir configuration install-dir)]
    (sh mkdir "-p" debian-dir)
    (duck/write-lines
     (path debian-dir "control")
     [(str "Source: "           artifact-id)
      (str "Section: "           (:section configuration section))
      (str "Priority: "          (:priority configuration priority))
      (str "Maintainer: "        (:maintainer configuration maintainer))
      (str "Build-Depends: "     (:buildDepends configuration build-depends))
      (str "Standards-Version: " (:standardsVersion configuration standards-version))
      (str "Homepage: "          (:homepage configuration homepage))
      ""
      (str "Package: "           artifact-id)
      (str "Architecture: "      (:architecture configuration architecture))
      (str "Depends: "           (format-dependencies dependencies))
      (str "Description: "       (format-description configuration))])
    (duck/write-lines
     (path debian-dir "changelog")
     [(str artifact-id " (" (:version configuration version) ") unstable; urgency=low")
      ""
      "  * Initial Release."
      ""
      (str " -- "
           (:maintainer configuration maintainer) "  "
           (.format (SimpleDateFormat. "EEE, d MMM yyyy HH:mm:ss Z"
                                       (Locale/CANADA)) (Date.)))])
    (duck/write-lines
     (path debian-dir "rules")
     ["#!/usr/bin/make -f" "%:"
      "\tdh $@"])
    (duck/write-lines
     (path package-dir "Makefile")
     [(str "INSTALLDIR := $(DESTDIR)/" install-dir)
      "build:"
      ""
      "install:"
      "\t@mkdir -p $(INSTALLDIR)"
      (str/join " "
                ["\t@cd" target-dir "&&"
                 copy "-a" (:files configuration files) "$(INSTALLDIR)"])])
    ((juxt write-preinst write-postinst write-prerm write-postrm)
     debian-dir configuration)
    (sh rm "-fr" "debhelper.log" :dir debian-dir )
    (.info (.getLog this) (sh debuild :dir debian-dir))))
