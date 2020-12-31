--
-- Copyright © 2016-2020 The Thingsboard Authors
--
-- Licensed under the Apache License, Version 2.0 (the "License");
-- you may not use this file except in compliance with the License.
-- You may obtain a copy of the License at
--
--     http://www.apache.org/licenses/LICENSE-2.0
--
-- Unless required by applicable law or agreed to in writing, software
-- distributed under the License is distributed on an "AS IS" BASIS,
-- WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
-- See the License for the specific language governing permissions and
-- limitations under the License.
--

CREATE TABLE IF NOT EXISTS broker_user (
    id uuid NOT NULL CONSTRAINT broker_user_pkey PRIMARY KEY,
    created_time bigint NOT NULL,
    additional_info varchar,
    authority varchar(255),
    email varchar(255) UNIQUE,
    first_name varchar(255),
    last_name varchar(255)
);

CREATE TABLE IF NOT EXISTS user_credentials (
    id uuid NOT NULL CONSTRAINT user_credentials_pkey PRIMARY KEY,
    created_time bigint NOT NULL,
    activate_token varchar(255) UNIQUE,
    enabled boolean,
    password varchar(255),
    reset_token varchar(255) UNIQUE,
    user_id uuid UNIQUE
);

CREATE TABLE IF NOT EXISTS mqtt_client (
    id uuid NOT NULL CONSTRAINT mqtt_client_pkey PRIMARY KEY,
    created_time bigint NOT NULL,
    client_id varchar(255),
    name varchar(255),
    created_by uuid,
    CONSTRAINT mqtt_client_id_unq_key UNIQUE (client_id)
);

CREATE TABLE IF NOT EXISTS mqtt_client_credentials (
    id uuid NOT NULL CONSTRAINT mqtt_client_credentials_pkey PRIMARY KEY,
    created_time bigint NOT NULL,
    client_id varchar(255),
    credentials_id varchar,
    credentials_type varchar(255),
    credentials_value varchar,
    CONSTRAINT mqtt_client_credentials_id_unq_key UNIQUE (credentials_id)
);