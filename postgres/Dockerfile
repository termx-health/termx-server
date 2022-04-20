FROM postgres:14.0

ADD init-scripts /docker-entrypoint-initdb.d
ADD manual-scripts /opt/scripts
RUN chmod +x -R /opt/scripts

ENV POSTGRES_PASSWORD=postgres
