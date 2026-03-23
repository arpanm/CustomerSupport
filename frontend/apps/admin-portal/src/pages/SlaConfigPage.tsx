import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { Card, CardContent, CardHeader, CardTitle, Spinner } from '@supporthub/ui';
import { useAuthStore } from '../store/authStore.js';
import { listCategories, listSlaPolicies, createSlaPolicy, updateSlaPolicy } from '../api/adminApi.js';
import type { CreateSlaPolicyRequest } from '../api/adminApi.js';

const PRIORITIES = ['LOW', 'MEDIUM', 'HIGH', 'URGENT'];

export function SlaConfigPage() {
  const { token } = useAuthStore();
  const queryClient = useQueryClient();
  const [editId, setEditId] = useState<string | null>(null);
  const [editForm, setEditForm] = useState<Partial<CreateSlaPolicyRequest>>({});

  const { data: categories } = useQuery({
    queryKey: ['categories'],
    queryFn: () => listCategories(token ?? ''),
    enabled: token != null,
  });

  const { data: policies, isLoading } = useQuery({
    queryKey: ['sla-policies'],
    queryFn: () => listSlaPolicies(token ?? ''),
    enabled: token != null,
  });

  const updateMutation = useMutation({
    mutationFn: ({ id, req }: { id: string; req: Partial<CreateSlaPolicyRequest> }) =>
      updateSlaPolicy(token ?? '', id, req),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['sla-policies'] });
      setEditId(null);
    },
  });

  const createMutation = useMutation({
    mutationFn: (req: CreateSlaPolicyRequest) => createSlaPolicy(token ?? '', req),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['sla-policies'] });
    },
  });

  const categoryMap = Object.fromEntries((categories ?? []).map((c) => [c.id, c.name]));

  return (
    <div className="flex flex-col gap-4">
      <h2 className="text-2xl font-bold text-gray-900">SLA Configuration</h2>
      <p className="text-sm text-gray-500">
        Configure first response and resolution time targets per category and priority.
      </p>

      {isLoading ? (
        <div className="flex h-32 items-center justify-center">
          <Spinner size="lg" label="Loading SLA policies..." />
        </div>
      ) : (
        <Card>
          <CardHeader>
            <CardTitle className="text-sm">SLA Policies</CardTitle>
          </CardHeader>
          <CardContent>
            <table className="w-full text-sm">
              <thead className="border-b border-gray-200">
                <tr>
                  <th className="pb-2 text-left font-medium text-gray-700">Category</th>
                  <th className="pb-2 text-left font-medium text-gray-700">Priority</th>
                  <th className="pb-2 text-left font-medium text-gray-700">First Response (h)</th>
                  <th className="pb-2 text-left font-medium text-gray-700">Resolution (h)</th>
                  <th className="pb-2 text-left font-medium text-gray-700">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100">
                {(policies ?? []).map((policy) => (
                  <tr key={policy.id}>
                    <td className="py-2 pr-4">
                      {categoryMap[policy.categoryId] ?? policy.categoryId}
                    </td>
                    <td className="py-2 pr-4 font-medium">{policy.priority}</td>
                    <td className="py-2 pr-4">
                      {editId === policy.id ? (
                        <input
                          type="number"
                          min={1}
                          value={editForm.firstResponseHours ?? policy.firstResponseHours}
                          onChange={(e) => {
                            setEditForm((f) => ({ ...f, firstResponseHours: Number(e.target.value) }));
                          }}
                          className="w-20 rounded border border-gray-300 px-2 py-1 text-sm"
                        />
                      ) : (
                        <span>{policy.firstResponseHours}h</span>
                      )}
                    </td>
                    <td className="py-2 pr-4">
                      {editId === policy.id ? (
                        <input
                          type="number"
                          min={1}
                          value={editForm.resolutionHours ?? policy.resolutionHours}
                          onChange={(e) => {
                            setEditForm((f) => ({ ...f, resolutionHours: Number(e.target.value) }));
                          }}
                          className="w-20 rounded border border-gray-300 px-2 py-1 text-sm"
                        />
                      ) : (
                        <span>{policy.resolutionHours}h</span>
                      )}
                    </td>
                    <td className="py-2">
                      {editId === policy.id ? (
                        <div className="flex gap-2">
                          <button
                            onClick={() => { updateMutation.mutate({ id: policy.id, req: editForm }); }}
                            className="text-xs text-blue-600 hover:underline"
                          >
                            Save
                          </button>
                          <button
                            onClick={() => { setEditId(null); }}
                            className="text-xs text-gray-500 hover:underline"
                          >
                            Cancel
                          </button>
                        </div>
                      ) : (
                        <button
                          onClick={() => {
                            setEditId(policy.id);
                            setEditForm({
                              firstResponseHours: policy.firstResponseHours,
                              resolutionHours: policy.resolutionHours,
                            });
                          }}
                          className="text-xs text-blue-600 hover:underline"
                        >
                          Edit
                        </button>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>

            {/* Add new policy */}
            <div className="mt-4 border-t border-gray-200 pt-4">
              <p className="mb-2 text-xs font-medium text-gray-700">Add new SLA policy:</p>
              <AddSlaPolicyForm
                categories={categories ?? []}
                onSave={(req) => { createMutation.mutate(req); }}
              />
            </div>
          </CardContent>
        </Card>
      )}
    </div>
  );
}

function AddSlaPolicyForm({
  categories,
  onSave,
}: {
  categories: { id: string; name: string }[];
  onSave: (req: CreateSlaPolicyRequest) => void;
}) {
  const [form, setForm] = useState<CreateSlaPolicyRequest>({
    categoryId: '',
    priority: 'MEDIUM',
    firstResponseHours: 4,
    resolutionHours: 24,
  });

  const update = <K extends keyof CreateSlaPolicyRequest>(k: K, v: CreateSlaPolicyRequest[K]) => {
    setForm((f) => ({ ...f, [k]: v }));
  };

  return (
    <form
      onSubmit={(e) => {
        e.preventDefault();
        if (form.categoryId.length > 0) onSave(form);
      }}
      className="flex flex-wrap items-end gap-3"
    >
      <div>
        <label className="block text-xs text-gray-600">Category</label>
        <select
          required
          value={form.categoryId}
          onChange={(e) => { update('categoryId', e.target.value); }}
          className="mt-1 rounded-md border border-gray-300 px-2 py-1.5 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
        >
          <option value="">Select category</option>
          {categories.map((c) => (
            <option key={c.id} value={c.id}>{c.name}</option>
          ))}
        </select>
      </div>
      <div>
        <label className="block text-xs text-gray-600">Priority</label>
        <select
          value={form.priority}
          onChange={(e) => { update('priority', e.target.value); }}
          className="mt-1 rounded-md border border-gray-300 px-2 py-1.5 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
        >
          {PRIORITIES.map((p) => <option key={p} value={p}>{p}</option>)}
        </select>
      </div>
      <div>
        <label className="block text-xs text-gray-600">First Response (h)</label>
        <input
          type="number" min={1} value={form.firstResponseHours}
          onChange={(e) => { update('firstResponseHours', Number(e.target.value)); }}
          className="mt-1 w-20 rounded-md border border-gray-300 px-2 py-1.5 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
        />
      </div>
      <div>
        <label className="block text-xs text-gray-600">Resolution (h)</label>
        <input
          type="number" min={1} value={form.resolutionHours}
          onChange={(e) => { update('resolutionHours', Number(e.target.value)); }}
          className="mt-1 w-20 rounded-md border border-gray-300 px-2 py-1.5 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
        />
      </div>
      <button
        type="submit"
        className="rounded-md bg-blue-600 px-3 py-1.5 text-sm font-medium text-white hover:bg-blue-700"
      >
        Add Policy
      </button>
    </form>
  );
}
