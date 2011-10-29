(ns com.swellimagination.debian.deploy-mojo
  (:require [clojure.contrib.duck-streams :as duck])
  (:use     [clojure.contrib.shell-out :only (sh)]
            [com.swellimagination.debian.plugin])
  (:import [java.io File])
  (:gen-class :name         com.swellimagination.debian.DeployMojo
              :extends      org.apache.maven.plugin.AbstractMojo
              :prefix       dm-
              :constructors {[org.apache.maven.project.MavenProject] []}
              :init         init
              :state        state
              :methods      [[execute [java.util.TreeMap java.net.URL] void]]))

(defn dm-init
  [project]
  [[] project])

(defmulti deploy
  (fn [this config package & {:keys [to]}]
    (.getProtocol to)))

(defmethod deploy
  "file"
  [this config package & {:keys [to]}]
  (let [mirror          (.getPath to)
        apt-move-config (:aptMoveConfig config (path mirror apt-move-config))
        apt-config      (:aptConfig     config (path mirror apt-config))
        pkg-config      (:packageConfig config (path mirror pkg-config))
        dist            (path mirror "dists" (:dist config dist))]
    (.info (.getLog this) (str "Deploying " package " to " (.getPath to)))
    (.info (.getLog this)
     (str
      (sh apt-move "-c" apt-move-config "movefile" package)
      (sh apt-ftparchive "-c" apt-config "generate" pkg-config
          :dir mirror)))
    (let [release (sh apt-ftparchive "-c" apt-config "release" dist)]
      (duck/copy release (File. (path dist "Release"))))))

(defn dm-execute
  [this options mirror]
  (let [config      (java->map options)
        project     (.state this)
        artifact-id (:name config (.getArtifactId project))
        version     (:version config (.getVersion project))
        arch        (:architecture config architecture)
        base-dir    (.getBasedir project)
        target-dir  (path base-dir (:targetDir config target-subdir))
        package     (path target-dir (str artifact-id vs version vs arch ".deb"))]
    (deploy this config package :to mirror)))
