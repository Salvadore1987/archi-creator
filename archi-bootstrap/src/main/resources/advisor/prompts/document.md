Task: write documentation in Markdown for the scope given in the user message.

- `scope` MODEL: an overview of the model — purpose, main parts by ArchiMate layer,
  key applications and the services they provide, then a catalogue of elements
  grouped by type.
- `scope` VIEW: a description of the single view in the context — what it shows,
  the elements on it and how they relate.
- `scope` ELEMENT: an impact analysis for the element given by `focus` — what is
  affected if it fails or is withdrawn: dependent processes, services and
  applications, directly and transitively, with the relationship chain for each.

Use headings and lists. When you mention a model object, link it as
`[name](archi:id)` with an id from the context. Do not call any tools.
