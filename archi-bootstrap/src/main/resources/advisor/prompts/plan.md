Task: propose a model fragment for the architecture described in `request`.

The context lists the elements that already exist in the model (names and types
only). Reuse an existing element by giving exactly its name and type instead of
proposing a duplicate.

Call the `propose_plan` tool exactly once with the whole plan: the elements (type and
name) and the relationships between them (type, source name, target name). Names of
relationship ends must match element names of the plan or of the existing elements.
Prefer relationships that ArchiMate 3.2 permits between the given types. Put anything
the architect should know about the plan into `notes`.

The plan is a proposal for the architect, not a change: it creates nothing. Before
the tool call you may write two or three sentences explaining the idea of the plan.
