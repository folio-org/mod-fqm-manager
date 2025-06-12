# Externally-submitted changesets

These changesets are not developed by the Corsair team for FQM and are instead generated based on external modules via [fqm-tools](https://github.com/folio-org/fqm-tools). As such, these files should **not** be manually edited and instead should only be edited by changing the [underlying modules](https://github.com/folio-org/fqm-tools/blob/main/run-config.yaml) in `fqm-tools`.

`provided-views.json` is a long-term list of every view that has ever been provided by an external module, to ensure that views no longer provided by `fqm-tools`-generated modules are dropped from the database and appropriately cleaned up.
