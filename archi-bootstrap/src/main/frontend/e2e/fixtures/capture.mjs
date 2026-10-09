// Снимок эталонной модели с работающего сервера — данные для E2E и замеров.
// Запуск: node e2e/fixtures/capture.mjs http://localhost:8080 <modelId>
// Сервер должен быть в профиле dev (вход заглушкой) с импортированным
// docs/Hamkorbank_AS_IS_strict.archimate.
import { writeFileSync } from 'node:fs';

const [base = 'http://localhost:8080', modelId] = process.argv.slice(2);
const get = async (path) => {
  const response = await fetch(`${base}/api/v1${path}`);
  if (!response.ok) throw new Error(`${path}: ${response.status}`);
  return response.json();
};

const tree = await get(`/models/${modelId}`);
const views = {};
for (const view of tree.views) {
  views[view.id] = await get(`/views/${view.id}`);
}
// Размещения считаются из представлений, если сервер их ещё не отдаёт.
tree.placements ??= Object.values(views).map((v) => ({
  viewId: v.id,
  elementIds: [...new Set(v.nodes.filter((n) => n.elementId).map((n) => n.elementId))],
  relationshipIds: [...new Set(v.edges.filter((e) => e.relationshipId).map((e) => e.relationshipId))],
}));
const findings = await get(`/models/${modelId}/validate`);
const elementTypes = await get('/metamodel/elements');
writeFileSync(
  new URL('./reference-model.json', import.meta.url),
  JSON.stringify({ tree, views, findings, elementTypes }),
);
console.log(`elements ${tree.elements.length}, relationships ${tree.relationships.length}, views ${tree.views.length}`);
