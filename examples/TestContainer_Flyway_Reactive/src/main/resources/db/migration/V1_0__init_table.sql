CREATE SEQUENCE id_sequence;

CREATE TABLE demo_entity
(
    id BIGINT PRIMARY KEY DEFAULT nextval('id_sequence')
)