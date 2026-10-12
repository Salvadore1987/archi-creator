import { Fragment, useState, type ReactNode } from 'react';
import type { AdvisorFinding, AdvisorStatus, ElementRef, ModelPlan } from '../api/types';
import { Icon } from '../app/Icon';
import { useCanEdit } from '../app/session';
import { t, typeName, type MessageKey } from '../i18n';
import { useEditor } from '../model/store';
import { parseMarkdown, type Inline } from './markdown';
import { useAdvisor } from './store';
import { useGoToRef } from './useGoToRef';
import './advisor.css';

/**
 * Панель помощника под «Свойствами»: ревью, подсказка по элементу, план по описанию,
 * документация — потоком. Всё, что здесь появляется, — текст, ссылки и предложения:
 * помощник не меняет модель, правки вносит архитектор.
 */
export function AdvisorPanel({ status }: { status: AdvisorStatus }) {
  const doc = useEditor((s) => s.doc!);
  const activeViewId = useEditor((s) => s.activeViewId);
  const selectedId = useEditor((s) => (s.selection.ids.length === 1 ? s.selection.ids[0] : undefined));
  const canEdit = useCanEdit();
  const advisor = useAdvisor();
  const goTo = useGoToRef();
  const [prompt, setPrompt] = useState('');

  const modelId = doc.model.id;
  const selected = selectedId ? doc.elements[selectedId] : undefined;
  const activeView = activeViewId ? doc.views[activeViewId] : undefined;
  const exhausted = status.budgetStatus === 'EXHAUSTED';
  // Подсказка и документация — сам ответ в Markdown; у ревью и плана текст — ход работы над находками и планом.
  const textual = advisor.fn === 'explain' || advisor.fn === 'document';

  return (
    <section className="ai" data-testid="advisor">
      <div className="panel-head">
        {t('advisor.title')}
        <span className="spacer" />
        {advisor.fn && <span className="chip">{t(`advisor.fn.${advisor.fn}` as MessageKey)}</span>}
      </div>

      <div className="ai__actions">
        <button type="button" className="btn btn--small" disabled={exhausted}
          onClick={() => void advisor.ask('review', { modelId })}>
          {t('advisor.review')}
        </button>
        {activeView && (
          <button type="button" className="btn btn--small" disabled={exhausted}
            onClick={() => void advisor.ask('review', { modelId, viewId: activeView.id })}>
            {t('advisor.reviewView')}
          </button>
        )}
        {selected && (
          <button type="button" className="btn btn--small" disabled={exhausted}
            onClick={() => void advisor.ask('explain', { modelId, elementId: selected.archiId })}>
            {t('advisor.explain')}
          </button>
        )}
        <button type="button" className="btn btn--small" disabled={exhausted}
          onClick={() => void advisor.ask('document', activeView ? { modelId, viewId: activeView.id } : { modelId })}>
          {t('advisor.documentModel')}
        </button>
      </div>

      <div className="ai__body" data-testid="advisor-body">
        {status.budgetStatus === 'WARNING' && <div className="ai__banner">{t('advisor.budgetWarning')}</div>}
        {exhausted && <div className="ai__banner ai__banner--err">{t('advisor.budgetExhausted', { date: status.resetsAt })}</div>}
        {advisor.meta?.contextTruncated && <div className="ai__banner">{t('advisor.truncated')}</div>}
        {advisor.error && <div className="ai__banner ai__banner--err" data-testid="advisor-error">{advisor.error}</div>}
        {!advisor.fn && !advisor.error && <p className="ai__idle">{t('advisor.idle')}</p>}
        {advisor.text && !textual && <p className="ai__muted ai__stream">{advisor.text}</p>}

        {advisor.findings.map((finding) => (
          <FindingCard key={finding.id} finding={finding} onGo={goTo} />
        ))}
        {advisor.fn === 'review' && advisor.meta?.outcome === 'COMPLETED' && (
          <p className="ai__muted">
            {advisor.findings.length ? t('advisor.findingsCount', { n: advisor.findings.length }) : t('advisor.findingsNone')}
          </p>
        )}
        {advisor.plan && <PlanView plan={advisor.plan} onGo={goTo} />}
        {advisor.answer ? (
          <MarkdownView markdown={advisor.answer.markdown} references={advisor.answer.references} onGo={goTo} />
        ) : (
          advisor.text && textual && <MarkdownView markdown={advisor.text} references={[]} onGo={goTo} />
        )}
        {advisor.running && <p className="ai__muted" data-testid="advisor-running">{t('advisor.running')}</p>}
        {advisor.answer && advisor.fn === 'document' && (
          <button type="button" className="btn btn--small" onClick={() => download(advisor.answer!.markdown, doc.model.name)}>
            <Icon name="download" /> {t('advisor.download')}
          </button>
        )}
        {advisor.meta && (
          <p className="ai__usage">
            {t('advisor.usage', { prompt: advisor.meta.promptTokens, completion: advisor.meta.completionTokens })}
          </p>
        )}
      </div>

      <div className="ai__note">
        <Icon name="eye" />
        {t('advisor.note')}
      </div>

      {canEdit && (
        <form
          className="ai__ask"
          onSubmit={(event) => {
            event.preventDefault();
            if (prompt.trim()) void advisor.ask('plan', { modelId, prompt: prompt.trim() });
          }}
        >
          <input
            value={prompt}
            maxLength={4000}
            placeholder={t('advisor.planPlaceholder')}
            onChange={(event) => setPrompt(event.target.value)}
            disabled={exhausted}
          />
          <button type="submit" className="btn btn--primary btn--small" disabled={exhausted || !prompt.trim()}>
            {t('advisor.planSubmit')}
          </button>
        </form>
      )}
      {advisor.running && (
        <button type="button" className="btn btn--small ai__stop" onClick={() => advisor.cancel()}>
          {t('advisor.stop')}
        </button>
      )}
    </section>
  );
}

function FindingCard({ finding, onGo }: { finding: AdvisorFinding; onGo: (ref: ElementRef) => Promise<void> }) {
  return (
    <div className={`finding sev-${finding.severity.toLowerCase()}`} data-testid="advisor-finding">
      <div className="finding__head">
        <Icon name="warn" />
        <span className="finding__title">{t(`advisor.severity.${finding.severity}` as MessageKey)}</span>
        <span className="finding__code">{finding.code}</span>
      </div>
      <div className="finding__body">{finding.message}</div>
      {finding.suggestion && <div className="finding__fix">{finding.suggestion}</div>}
      <div className="finding__act">
        <button type="button" className="btn btn--ghost btn--small" onClick={() => void onGo(finding.target)}>
          {t('advisor.goTo')}
        </button>
        <span className="finding__target">{finding.target.name}</span>
      </div>
    </div>
  );
}

function PlanView({ plan, onGo }: { plan: ModelPlan; onGo: (ref: ElementRef) => Promise<void> }) {
  return (
    <div className="plan" data-testid="advisor-plan">
      <h3>{t('advisor.planTitle')}</h3>
      <h4>{t('advisor.planElements')}</h4>
      {plan.elements.map((element, index) => (
        <div key={`${element.archiType}-${element.name}`} className="plan__row">
          <span className="plan__n">{index + 1}</span>
          <span className="plan__type">{typeName(element.archiType)}</span>
          <span className="plan__name">{element.name}</span>
          {element.existingRef && (
            <button type="button" className="btn btn--ghost btn--small" onClick={() => void onGo(element.existingRef!)}>
              {t('advisor.planExisting')}
            </button>
          )}
        </div>
      ))}
      {plan.relationships.length > 0 && <h4>{t('advisor.planRelations')}</h4>}
      {plan.relationships.map((relation, index) => (
        <div key={index} className={`plan__row${relation.permitted ? '' : ' plan__row--warn'}`} data-permitted={relation.permitted}>
          <span className="plan__name">{relation.sourceName}</span>
          <span className="plan__type">{typeName(relation.archiType)}</span>
          <span className="plan__name">{relation.targetName}</span>
          {!relation.permitted && <span className="chip chip--err">{t('advisor.planNotPermitted')}</span>}
        </div>
      ))}
      {plan.notes && <p className="ai__muted">{plan.notes}</p>}
    </div>
  );
}

function MarkdownView({ markdown, references, onGo }: {
  markdown: string;
  references: ElementRef[];
  onGo: (ref: ElementRef) => Promise<void>;
}) {
  const names = new Map(references.map((r) => [r.archiId, r.name]));
  const inline = (parts: Inline[]): ReactNode =>
    parts.map((part, index) => {
      switch (part.kind) {
        case 'strong':
          return <strong key={index}>{part.text}</strong>;
        case 'code':
          return <code key={index}>{part.text}</code>;
        case 'ref':
          return (
            <button key={index} type="button" className="ai__ref"
              onClick={() => void onGo({ archiId: part.archiId, name: names.get(part.archiId) ?? part.text })}>
              {part.text}
            </button>
          );
        default:
          return <Fragment key={index}>{part.text}</Fragment>;
      }
    });
  return (
    <div className="ai__md" data-testid="advisor-answer">
      {parseMarkdown(markdown).map((block, index) => {
        if (block.kind === 'heading') {
          return block.level <= 2 ? <h3 key={index}>{inline(block.inline)}</h3> : <h4 key={index}>{inline(block.inline)}</h4>;
        }
        if (block.kind === 'list') {
          const items = block.items.map((item, i) => <li key={i}>{inline(item)}</li>);
          return block.ordered ? <ol key={index}>{items}</ol> : <ul key={index}>{items}</ul>;
        }
        return <p key={index}>{inline(block.inline)}</p>;
      })}
    </div>
  );
}

function download(markdown: string, name: string) {
  const url = URL.createObjectURL(new Blob([markdown], { type: 'text/markdown;charset=utf-8' }));
  const link = document.createElement('a');
  link.href = url;
  link.download = `${name}.md`;
  link.click();
  URL.revokeObjectURL(url);
}
