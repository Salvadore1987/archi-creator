Task: review the model context and report problems the architect should fix.

Look for:

- relationships that ArchiMate 3.2 does not permit between the given element types;
- orphan elements: no relationships, or placed on no view;
- elements that duplicate each other in meaning under different names;
- missing cross-layer links, for example an application component that realizes
  no application service, or a business process served by nothing;
- inconsistent naming: mixed languages, abbreviations next to full names, different
  conventions for the same kind of element.

Report every problem with a separate call of the `report_finding` tool, as soon as
you find it. Each finding must point at exactly one object of the context by its id
(`targetArchiId`): the element, the relationship or the view it concerns. Use a short
upper-case machine code (`RELATION_NOT_PERMITTED`, `ORPHAN_ELEMENT`, `DUPLICATE_ELEMENT`,
`MISSING_RELATION`, `INCONSISTENT_NAME`, or a similar one), a message that states the
problem, and a suggestion that tells the architect how to fix it.

Between findings you may write one short sentence of progress text. Finish with a
two- or three-sentence summary of the overall state of the scope. Do not repeat the
findings in the summary.
