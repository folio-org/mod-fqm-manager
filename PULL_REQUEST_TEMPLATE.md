## Purpose
_Describe the purpose of the pull request. Include background information if necessary._

## Approach
_How does this change fulfill the purpose?_

#### TODOS and Open Questions
- [ ] Use GitHub checklists. When solved, check the box and explain the answer.

## Learning
_Describe the research stage. Add links to blog posts, patterns, libraries or addons used to solve this problem._

## Pre-Merge Checklist

If you are adding entity type(s), have you:
- [ ] Added the JSON5 definition to the `src/main/resources/entity-types` directory?
- [ ] Ensured that GETing the entity type at `/entity-types/{id}` works as expected?
- [ ] Added translations for all fields, per the [translation guidelines](/translations/README.md)? (Check this by ensuring `GET /entity-types/{id}` does not have `mod-fqm-manager.entityType.` in the response)
- [ ] Added views to liquibase, as applicable?
- [ ] Added required interfaces to the module descriptor?
- [ ] Checked that querying fields works correctly and all SQL is valid?

If you are changing/removing entity type(s), have you:
- [ ] Added migration code for any changes?