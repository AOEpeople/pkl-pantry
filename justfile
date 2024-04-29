#!/usr/bin/env just --justfile

list:
  just -l

# Converts the given yaml or json file to pkl format
@to-pkl file:
  #!/usr/bin/env bash
  pkl eval \
   --property "file={{file}}" \
   -- converter.pkl

# Generates modules from jsonschema sources.
@jsonschema-to-pkl url:
  #!/usr/bin/env bash
  pkl eval package://pkg.pkl-lang.org/pkl-pantry/org.json_schema.contrib@1.0.4#/generate.pkl \
  -m ./.tmp/gen/ \
  -p source="{{url}}"
