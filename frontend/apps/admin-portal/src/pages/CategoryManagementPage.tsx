import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { Card, CardContent, CardHeader, CardTitle, Spinner, Badge } from '@supporthub/ui';
import { useAuthStore } from '../store/authStore.js';
import {
  listCategories,
  createCategory,
  toggleCategory,
} from '../api/adminApi.js';
import type { CreateCategoryRequest } from '../api/adminApi.js';

const PRIORITY_OPTIONS = ['LOW', 'MEDIUM', 'HIGH', 'URGENT'];

function CategoryForm({ onSave, onCancel }: { onSave: (r: CreateCategoryRequest) => void; onCancel: () => void }) {
  const [name, setName] = useState('');
  const [slug, setSlug] = useState('');
  const [description, setDescription] = useState('');
  const [firstResponse, setFirstResponse] = useState(4);
  const [resolution, setResolution] = useState(24);
  const [priority, setPriority] = useState('MEDIUM');

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    onSave({
      name: name.trim(),
      slug: slug.trim().toLowerCase().replace(/\s+/g, '_'),
      description: description.trim() || undefined,
      slaFirstResponseHours: firstResponse,
      slaResolutionHours: resolution,
      defaultPriority: priority,
    });
  };

  return (
    <form onSubmit={handleSubmit} className="flex flex-col gap-4">
      <div className="grid grid-cols-2 gap-4">
        <div>
          <label className="block text-sm font-medium text-gray-700">Name</label>
          <input
            required
            value={name}
            onChange={(e) => {
              setName(e.target.value);
              setSlug(e.target.value.toLowerCase().replace(/\s+/g, '_').replace(/[^a-z0-9_]/g, ''));
            }}
            className="mt-1 w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
            placeholder="Order Issue"
          />
        </div>
        <div>
          <label className="block text-sm font-medium text-gray-700">Slug</label>
          <input
            required
            value={slug}
            onChange={(e) => { setSlug(e.target.value); }}
            className="mt-1 w-full rounded-md border border-gray-300 px-3 py-2 text-sm font-mono focus:outline-none focus:ring-2 focus:ring-blue-500"
            placeholder="order_issue"
          />
        </div>
        <div className="col-span-2">
          <label className="block text-sm font-medium text-gray-700">Description</label>
          <input
            value={description}
            onChange={(e) => { setDescription(e.target.value); }}
            className="mt-1 w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
            placeholder="Optional description"
          />
        </div>
        <div>
          <label className="block text-sm font-medium text-gray-700">First Response SLA (hours)</label>
          <input
            type="number"
            min={1}
            max={72}
            value={firstResponse}
            onChange={(e) => { setFirstResponse(Number(e.target.value)); }}
            className="mt-1 w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
          />
        </div>
        <div>
          <label className="block text-sm font-medium text-gray-700">Resolution SLA (hours)</label>
          <input
            type="number"
            min={1}
            max={168}
            value={resolution}
            onChange={(e) => { setResolution(Number(e.target.value)); }}
            className="mt-1 w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
          />
        </div>
        <div>
          <label className="block text-sm font-medium text-gray-700">Default Priority</label>
          <select
            value={priority}
            onChange={(e) => { setPriority(e.target.value); }}
            className="mt-1 w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
          >
            {PRIORITY_OPTIONS.map((p) => (
              <option key={p} value={p}>{p}</option>
            ))}
          </select>
        </div>
      </div>
      <div className="flex justify-end gap-2">
        <button
          type="button"
          onClick={onCancel}
          className="rounded-md border border-gray-300 px-4 py-2 text-sm text-gray-700 hover:bg-gray-50"
        >
          Cancel
        </button>
        <button
          type="submit"
          className="rounded-md bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-700"
        >
          Save Category
        </button>
      </div>
    </form>
  );
}

export function CategoryManagementPage() {
  const { token } = useAuthStore();
  const queryClient = useQueryClient();
  const [showForm, setShowForm] = useState(false);

  const { data: categories, isLoading } = useQuery({
    queryKey: ['categories'],
    queryFn: () => listCategories(token ?? ''),
    enabled: token != null,
  });

  const createMutation = useMutation({
    mutationFn: (req: CreateCategoryRequest) => createCategory(token ?? '', req),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['categories'] });
      setShowForm(false);
    },
  });

  const toggleMutation = useMutation({
    mutationFn: ({ id, isActive }: { id: string; isActive: boolean }) =>
      toggleCategory(token ?? '', id, isActive),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['categories'] });
    },
  });

  return (
    <div className="flex flex-col gap-4">
      <div className="flex items-center justify-between">
        <h2 className="text-2xl font-bold text-gray-900">Category Management</h2>
        <button
          onClick={() => { setShowForm(true); }}
          className="rounded-md bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-700"
        >
          + Add Category
        </button>
      </div>

      {showForm && (
        <Card>
          <CardHeader>
            <CardTitle className="text-sm">New Category</CardTitle>
          </CardHeader>
          <CardContent>
            <CategoryForm
              onSave={(req) => { createMutation.mutate(req); }}
              onCancel={() => { setShowForm(false); }}
            />
          </CardContent>
        </Card>
      )}

      {isLoading ? (
        <div className="flex h-32 items-center justify-center">
          <Spinner size="lg" label="Loading categories..." />
        </div>
      ) : (
        <div className="flex flex-col gap-3">
          {(categories ?? []).map((cat) => (
            <Card key={cat.id}>
              <CardContent className="py-4">
                <div className="flex items-start justify-between gap-4">
                  <div className="flex-1">
                    <div className="flex items-center gap-2">
                      <span className="font-medium text-gray-900">{cat.name}</span>
                      <code className="rounded bg-gray-100 px-1.5 py-0.5 text-xs text-gray-600">
                        {cat.slug}
                      </code>
                      <Badge variant={cat.isActive ? 'default' : 'secondary'}>
                        {cat.isActive ? 'Active' : 'Inactive'}
                      </Badge>
                    </div>
                    {cat.description.length > 0 && (
                      <p className="mt-0.5 text-sm text-gray-500">{cat.description}</p>
                    )}
                    <div className="mt-1 flex gap-4 text-xs text-gray-500">
                      <span>First Response: {cat.slaFirstResponseHours}h</span>
                      <span>Resolution: {cat.slaResolutionHours}h</span>
                      <span>Default Priority: {cat.defaultPriority}</span>
                    </div>
                  </div>
                  <button
                    onClick={() => { toggleMutation.mutate({ id: cat.id, isActive: !cat.isActive }); }}
                    className="text-sm text-blue-600 hover:underline"
                  >
                    {cat.isActive ? 'Deactivate' : 'Activate'}
                  </button>
                </div>
              </CardContent>
            </Card>
          ))}
        </div>
      )}
    </div>
  );
}
