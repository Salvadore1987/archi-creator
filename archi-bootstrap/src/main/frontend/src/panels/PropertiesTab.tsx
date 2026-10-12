import { useEffect, useRef, useState } from 'react';
import type { Property, Uuid, ViewNode } from '../api/types';
import { Icon } from '../app/Icon';
import { layerName, t, typeName } from '../i18n';
import { placementIndex, type ModelDoc } from '../model/doc';
import { editNodeText, updateContent } from '../model/ops';
import { useEditor } from '../model/store';
import { folderPath } from '../tree/buildTree';
import { layerToken } from '../canvas/style';
import { StyleEditor } from './StyleEditor';

/**
 * Форма объекта модели: имя, документация и свойства меняют элемент или
 * связь — а значит, видны на всех представлениях. Каждое поле фиксируется
 * при уходе фокуса отдельной операцией истории.
 */
export function PropertiesTab({ id, editable }: { id: Uuid; editable: boolean }) {
  const doc = useEditor((s) => s.doc!);
  const perform = useEditor((s) => s.perform);
  const element = doc.elements[id];
  const relationship = doc.relationships[id];
  const folder = doc.folders[id];
  const view = doc.views[id];
  const object = element ?? relationship;
  const opaque = object ? !object.supported : false;
  const readOnly = !editable || opaque || !object;

  const activeViewId = useEditor((s) => s.activeViewId);
  const annotation = activeViewId ? doc.loadedViews[activeViewId]?.nodes[id] : undefined;
  if (annotation && (annotation.kind === 'GROUP' || annotation.kind === 'NOTE')) {
    return <AnnotationProps viewId={activeViewId!} node={annotation} editable={editable} />;
  }

  const commit = (label: string, patch: { name?: string; documentation?: string; properties?: Property[] }) => {
    const edit = updateContent(useEditor.getState().doc!, id, patch);
    perform(label, edit.changes, edit.affected);
  };

  if (!object) {
    const name = folder?.name ?? view?.name ?? '';
    return (
      <div className="props scroll">
        <Field label={folder ? t('props.folderSelected') : t('props.viewSelected')}>
          <input className="field__input" value={name} readOnly />
        </Field>
        <Field label={t('props.folder')}>
          <div className="field__static">{folderPath(doc, folder?.parentId ?? view?.folderId ?? '')}</div>
        </Field>
        <Field label={t('props.identifier')}>
          <div className="field__mono">{folder?.archiId ?? view?.archiId}</div>
        </Field>
      </div>
    );
  }

  const placements = placementIndex(doc).get(id) ?? [];
  const name = object.name ?? '';

  return (
    <div className="props scroll" data-testid="properties">
      {opaque && <div className="props__note">{t('props.opaque')}</div>}
      {!editable && !opaque && <div className="props__note">{t('props.readOnly')}</div>}
      <Field label={t('props.name')}>
        <CommitInput
          value={name}
          readOnly={readOnly}
          testId="prop-name"
          onCommit={(value) => commit(t('props.renameOp', { from: name, to: value }), { name: value })}
        />
      </Field>
      <Field label={t('props.type')}>
        <div className="field__static">
          {typeName(object.archiType)} <span className="field__mono">{object.archiType}</span>
        </div>
      </Field>
      {element && (
        <Field label={t('props.layer')}>
          <div className="field__static">{layerName(element.layer)}</div>
        </Field>
      )}
      {relationship && (
        <>
          <Field label={t('props.source')}>
            <div className="field__static">{endName(doc, relationship.sourceId)}</div>
          </Field>
          <Field label={t('props.target')}>
            <div className="field__static">{endName(doc, relationship.targetId)}</div>
          </Field>
        </>
      )}
      <Field label={t('props.folder')}>
        <div className="field__static">{folderPath(doc, object.folderId)}</div>
      </Field>
      <Field label={t('props.documentation')}>
        <CommitInput
          multiline
          value={object.documentation ?? ''}
          readOnly={readOnly}
          testId="prop-documentation"
          onCommit={(value) => commit(t('props.documentationOp', { name }), { documentation: value })}
        />
      </Field>
      <Field label={t('props.properties')}>
        <PropertiesEditor
          properties={object.properties}
          readOnly={readOnly}
          onCommit={(properties) => commit(t('props.propertiesOp', { name }), { properties })}
        />
      </Field>
      {activeViewId && doc.loadedViews[activeViewId] && (
        <Field label={t('props.appearance')}>
          <Appearance viewId={activeViewId} objectId={id} name={name} readOnly={readOnly} fallbackFill={`--layer-${layerToken(element?.layer)}`} />
        </Field>
      )}
      <Field label={t('props.views')}>
        <div className="kvs">
          <div className="kv">
            <b>{placements.length}</b>
            <span>{placements.map((v) => doc.views[v]?.name).join(', ')}</span>
          </div>
        </div>
      </Field>
      <Field label={t('props.identifier')}>
        <div className="field__mono">{object.archiId}</div>
      </Field>
    </div>
  );
}

/** Группа и заметка — объекты представления, а не модели: правится подпись или текст узла. */
function AnnotationProps({ viewId, node, editable }: { viewId: Uuid; node: ViewNode; editable: boolean }) {
  const perform = useEditor((s) => s.perform);
  const group = node.kind === 'GROUP';
  const commit = (value: string) => {
    const view = useEditor.getState().doc!.loadedViews[viewId]!;
    const edit = editNodeText(view, node.id, group ? { label: value } : { content: value });
    perform(group ? t('props.groupLabelOp', { name: value }) : t('props.noteTextOp'), edit.changes, edit.affected);
  };
  return (
    <div className="props scroll" data-testid="properties">
      {!editable && <div className="props__note">{t('props.readOnly')}</div>}
      <Field label={group ? t('props.groupLabel') : t('props.noteText')}>
        <CommitInput
          multiline={!group}
          value={(group ? node.label : node.content) ?? ''}
          readOnly={!editable}
          testId={group ? 'prop-label' : 'prop-content'}
          onCommit={commit}
        />
      </Field>
      <Field label={t('props.type')}>
        <div className="field__static">
          {typeName(node.archiType)} <span className="field__mono">{node.archiType}</span>
        </div>
      </Field>
      <Field label={t('props.appearance')}>
        <StyleEditor
          viewId={viewId}
          nodeIds={[node.id]}
          edgeIds={[]}
          name={(group ? node.label : node.content) ?? typeName(node.archiType)}
          readOnly={!editable}
          fallbackFill={group ? '--group-fill' : '--note-fill'}
        />
      </Field>
      <Field label={t('props.identifier')}>
        <div className="field__mono">{node.archiId}</div>
      </Field>
    </div>
  );
}

/** Оформление элемента или связи — на их узлах и рёбрах открытого представления. */
function Appearance(props: { viewId: Uuid; objectId: Uuid; name: string; readOnly: boolean; fallbackFill: string }) {
  const view = useEditor((s) => s.doc!.loadedViews[props.viewId]);
  const nodeIds = Object.values(view?.nodes ?? {}).filter((n) => n.elementId === props.objectId).map((n) => n.id);
  const edgeIds = Object.values(view?.edges ?? {}).filter((e) => e.relationshipId === props.objectId).map((e) => e.id);
  if (nodeIds.length === 0 && edgeIds.length === 0) {
    return <div className="field__static">{t('props.appearanceNone')}</div>;
  }
  return (
    <StyleEditor
      viewId={props.viewId}
      nodeIds={nodeIds}
      edgeIds={edgeIds}
      name={props.name}
      readOnly={props.readOnly}
      fallbackFill={props.fallbackFill}
    />
  );
}

function endName(doc: ModelDoc, id: Uuid): string {
  return doc.elements[id]?.name ?? doc.relationships[id]?.name ?? typeName(doc.relationships[id]?.archiType ?? '');
}

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="field">
      <span className="field__label">{label}</span>
      {children}
    </div>
  );
}

/** Поле, фиксирующее значение по Enter или уходу фокуса, а не на каждую букву: одна правка — одна операция. */
function CommitInput(props: {
  value: string;
  readOnly: boolean;
  multiline?: boolean;
  testId?: string;
  onCommit(value: string): void;
}) {
  const [draft, setDraft] = useState(props.value);
  useEffect(() => setDraft(props.value), [props.value]);
  const commit = () => {
    if (!props.readOnly && draft !== props.value) props.onCommit(draft);
  };
  const common = {
    className: `field__input${props.multiline ? ' field__input--ta' : ''}`,
    value: draft,
    readOnly: props.readOnly,
    'data-testid': props.testId,
    onBlur: commit,
  };
  return props.multiline ? (
    <textarea {...common} onChange={(event) => setDraft(event.target.value)} />
  ) : (
    <input
      {...common}
      onChange={(event) => setDraft(event.target.value)}
      onKeyDown={(event) => {
        if (event.key === 'Enter') commit();
        if (event.key === 'Escape') setDraft(props.value);
      }}
    />
  );
}

function PropertiesEditor(props: { properties: Property[]; readOnly: boolean; onCommit(properties: Property[]): void }) {
  const [rows, setRowsState] = useState(props.properties);
  // Последние строки — в ref, а не только в состоянии: уход фокуса может
  // прийти раньше перерисовки, и фиксировать надо то, что набрано, а не
  // то, что было в замыкании прошлого рендера.
  const latest = useRef(props.properties);
  const setRows = (next: Property[]) => {
    latest.current = next;
    setRowsState(next);
  };
  useEffect(() => {
    latest.current = props.properties;
    setRowsState(props.properties);
  }, [props.properties]);
  const commit = (next: Property[]) => {
    if (JSON.stringify(next) !== JSON.stringify(props.properties)) props.onCommit(next);
  };
  if (props.readOnly) {
    return (
      <div className="kvs">
        {rows.map((p, i) => (
          <div className="kv" key={i}>
            <b>{p.key}</b>
            <span>{p.value}</span>
          </div>
        ))}
      </div>
    );
  }
  return (
    <div
      className="kvs"
      onBlur={(event) => {
        if (!event.currentTarget.contains(event.relatedTarget as Node)) commit(latest.current);
      }}
    >
      {rows.map((p, i) => (
        <div className="kv kv--edit" key={i}>
          <input
            className="kv__input"
            value={p.key}
            placeholder={t('props.key')}
            onChange={(event) => setRows(latest.current.map((r, j) => (j === i ? { ...r, key: event.target.value } : r)))}
          />
          <input
            className="kv__input"
            value={p.value}
            placeholder={t('props.value')}
            onChange={(event) => setRows(latest.current.map((r, j) => (j === i ? { ...r, value: event.target.value } : r)))}
          />
          <button
            type="button"
            className="btn btn--icon btn--small"
            title={t('props.removeProperty')}
            onClick={() => {
              const next = latest.current.filter((_, j) => j !== i);
              setRows(next);
              commit(next);
            }}
          >
            <Icon name="close" />
          </button>
        </div>
      ))}
      <button type="button" className="btn btn--small btn--ghost" onClick={() => setRows([...latest.current, { key: '', value: '' }])}>
        <Icon name="plus" />
        {t('props.addProperty')}
      </button>
    </div>
  );
}
