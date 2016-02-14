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
  `hour`      TINYINT     NOT NULL,
  `minute`    TINYINT     NOT NULL,
  UNIQUE KEY `uniqueTime` (`busId`, `dayType`, `direction`)
) DEFAULT CHARSET=utf8;

CREATE TABLE `Stop` (
  `id`        SMALLINT     NOT NULL PRIMARY KEY,
  `name`      VARCHAR(128) NOT NULL,
  `busId`     SMALLINT     NOT NULL,
  `direction` VARCHAR(32)  NOT NULL,
  `latitude`  FLOAT        NOT NULL,
  `longitude` FLOAT        NOT NULL
) DEFAULT CHARSET=utf8;

CREATE TABLE `Route` (
  `busId`     SMALLINT     NOT NULL,
  `direction` VARCHAR(32)  NOT NULL,
  `latitude`  FLOAT        NOT NULL,
  `longitude` FLOAT        NOT NULL,
  UNIQUE KEY `uniquePoint` (`busId`, `direction`, `latitude`, `longitude`)
) DEFAULT CHARSET=utf8;

# --- !Downs
