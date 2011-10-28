(ns com.swellimagination.debian.deploy-mojo
  (:require [clojure.string :as str])
  (:use     [clojure.contrib.shell-out :only (sh)]
            [com.swellimagination.debian.plugin])
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
  (fn [package & {:keys [to]}]
    (.getProtocol to)))

(defmethod deploy
  "file"
  [package & {:keys [to]}]
  (let [mirror (.getPath to)]
    (println "deploying" package "to" (.getPath to))))

(defn dm-execute
  [this options mirror]
  (let [config      (java->map options)
        project     (.state this)
        artifact-id (:name config (.getArtifactId project))
        version     (:version config (.getVersion project))
        arch        (:architecture config architecture)
        base-dir    (.getBasedir project)
        target-dir  (str/join "/" [base-dir (:targetDir config target-subdir)])
        package     (str/join "/" [target-dir (str artifact-id vs version vs arch ".deb")])]
    (deploy package :to mirror)))
