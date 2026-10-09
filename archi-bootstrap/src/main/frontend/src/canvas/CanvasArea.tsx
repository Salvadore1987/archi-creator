import { ReactFlowProvider } from '@xyflow/react';
import { Icon } from '../app/Icon';
import { t } from '../i18n';
import { useEditor } from '../model/store';
import { CanvasView } from './CanvasView';
import { EdgeMarkers } from './EdgeMarkers';

/**
 * Вкладки открытых представлений и холст активной. Масштаб и прокрутка
 * каждой вкладки хранятся в документе сессии, выделение общее для модели —
 * переключение вкладки ничего не теряет.
 */
export function CanvasArea() {
  const openViews = useEditor((s) => s.openViews);
  const activeViewId = useEditor((s) => s.activeViewId);
  const views = useEditor((s) => s.doc!.views);
  const loaded = useEditor((s) => (activeViewId ? !!s.doc!.loadedViews[activeViewId] : false));
  const openView = useEditor((s) => s.openView);
  const closeView = useEditor((s) => s.closeView);

  return (
    <>
      <EdgeMarkers />
      <div className="tabbar" role="tablist">
        <div className="tabs">
          {openViews.map((id) => (
            <div
              key={id}
              role="tab"
              aria-selected={id === activeViewId}
              className={`tab${id === activeViewId ? ' is-on' : ''}`}
              data-view-tab={id}
              onClick={() => openView(id)}
              onAuxClick={(event) => event.button === 1 && closeView(id)}
            >
              <Icon name="view" />
              <span>{views[id]?.name ?? ''}</span>
              <button
                type="button"
                className="tab__close"
                title={t('canvas.close')}
                onClick={(event) => {
                  event.stopPropagation();
                  closeView(id);
                }}
              >
                <Icon name="close" />
              </button>
            </div>
          ))}
        </div>
      </div>
      <div className="stage">
        {activeViewId && loaded ? (
          <ReactFlowProvider key={activeViewId}>
            <CanvasView viewId={activeViewId} />
          </ReactFlowProvider>
        ) : (
          <div className="notice">{activeViewId ? t('canvas.loading') : t('canvas.empty')}</div>
        )}
      </div>
    </>
  );
}
