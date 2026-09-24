# Field Atlas 1.1.8

Field Atlas 1.1.8 fixes a retrieval bug found during the bounty audit. Search
terms now use safe prefix normalization for ordinary inflections while keeping
the strict AND requirement, so a question such as “What causes Earth's seasons
and why do hemispheres have opposite seasons?” retrieves the local seasons
passage instead of incorrectly falling back to uncited model-only output.

The fix was verified on the Infinix SMART 20 / X6840: the device benchmark
seasons row changed from `0 evidence chunks` to `1 evidence chunk`.
