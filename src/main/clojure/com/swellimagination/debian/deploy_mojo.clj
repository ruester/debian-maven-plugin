(ns com.swellimagination.debian.deploy-mojo
  (:require [clojure.contrib.duck-streams :as duck]
            [clojure.string :as str])
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

(defn- apt-config-gen
  [[config target-dir mirror] k default-file replaces]
  (if (k config)
    [config target-dir mirror]
    (let [config-file (path mirror "debian" default-file)
          dst-file    (path target-dir (last (str/split default-file #"/")))]
      (duck/with-out-writer dst-file
        (doseq [line    (duck/read-lines config-file)
                replace replaces]
          (println (str/replace line (first replace) (second replace)))))
      [(assoc config k dst-file) target-dir mirror])))

(defn- apt-move-config
  [[config target-dir mirror]]
  (apt-config-gen [config target-dir mirror] :aptMoveConfig apt-move-config-file
                  [[#"LOCALDIR=.*" (str "LOCALDIR=" (path mirror "debian"))]]))

(defn- apt-config
  [[config target-dir mirror]]
  (apt-config-gen [config target-dir mirror] :aptConfig apt-config-file [["" ""]]))

(defn- apt-pkg-config
  [[config target-dir mirror]]
  (apt-config-gen [config target-dir mirror] :packageConfig pkg-config-file
                  [[#"ArchiveDir .*" (str "ArchiveDir " (path mirror "debian") ";")]]))

(defmulti deploy
  (fn [this config target-dir package & {:keys [to]}]
    (.getProtocol to)))

(defmethod deploy
  "file"
  [this config target-dir package & {:keys [to]}]
  (let [mirror          (.getPath to)
        config          (first (-> [config target-dir mirror]
                                   apt-move-config
                                   apt-config
                                   apt-pkg-config))
        apt-move-config (:aptMoveConfig config)
        apt-config      (:aptConfig     config)
        pkg-config      (:packageConfig config)
        dist            (path mirror "debian/dists" (:dist config dist))]
    (.info (.getLog this) (str "Deploying " package " to " (.getPath to)))
    (.info (.getLog this)
     (str
      (sh apt-move "-c" apt-move-config "movefile" package)
      (sh apt-ftparchive "-c" apt-config "generate" pkg-config
          :dir (path mirror "debian"))))
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
    (deploy this config target-dir package :to mirror)))
