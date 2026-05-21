# Custom Elasticsearch image with the ICU analysis plugin pre-installed.
#
# The plugin enables `icu_analyzer`, which is what IndexInitializer requests when
# creating the files / folders indices. Installing the plugin at image build time
# (rather than via a container-startup script) ensures every fresh container has
# ICU available before Elasticsearch starts listening, so IndexInitializer never
# races with plugin installation.
#
# Same image is used for dev (docker-compose) and prod, so analyzer behavior is
# identical across environments — important for Vietnamese diacritic handling.

FROM elasticsearch:9.0.3

RUN elasticsearch-plugin install --batch analysis-icu
