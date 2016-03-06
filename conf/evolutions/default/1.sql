# --- !Ups

CREATE TABLE `Bus` (
  `id`        SMALLINT     NOT NULL PRIMARY KEY,
  `departure` VARCHAR(128) NOT NULL,
  `arrival`   VARCHAR(128) NOT NULL
) DEFAULT CHARSET=utf8;

CREATE TABLE `Time` (
  `busId`     SMALLINT    NOT NULL,
  `dayType`   VARCHAR(32) NOT NULL,
  `direction` VARCHAR(32) NOT NULL,
  `time`      CHAR(5)     NOT NULL,
  UNIQUE KEY `uniqueTime` (`busId`, `dayType`, `direction`, `time`)
) DEFAULT CHARSET=utf8;

CREATE TABLE `Stop` (
  `id`        MEDIUMINT    NOT NULL,
  `name`      VARCHAR(128) NOT NULL,
  `busId`     SMALLINT     NOT NULL,
  `direction` VARCHAR(32)  NOT NULL,
  `latitude`  DOUBLE       NOT NULL,
  `longitude` DOUBLE       NOT NULL,
  UNIQUE KEY `uniqueStop` (`id`, `busId`, `direction`)
) DEFAULT CHARSET=utf8;

CREATE TABLE `RoutePoint` (
  `busId`       SMALLINT     NOT NULL,
  `direction`   VARCHAR(32)  NOT NULL,
  `description` VARCHAR(128) NOT NULL,
  `latitude`    DOUBLE       NOT NULL,
  `longitude`   DOUBLE       NOT NULL,
  UNIQUE KEY `uniquePoint` (`busId`, `direction`, `latitude`, `longitude`)
) DEFAULT CHARSET=utf8;

# --- !Downs
