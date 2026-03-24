import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { Card, CardContent, CardHeader, CardTitle, Spinner, Badge } from '@supporthub/ui';
import { useAuthStore } from '../store/authStore.js';
import {
  listFaqs,
  createFaq,
  updateFaq,
  deleteFaq,
  toggleFaqPublish,
} from '../api/adminApi.js';
import type { FAQ, CreateFAQRequest } from '../api/adminApi.js';

// ---------------------------------------------------------------------------
// FAQ modal (create / edit)
// ---------------------------------------------------------------------------

interface FaqModalProps {
  initial?: FAQ;
  onSave: (req: CreateFAQRequest) => void;
  onCancel: () => void;
  saving: boolean;
}

function FaqModal({ initial, onSave, onCancel, saving }: FaqModalProps) {
  const [question, setQuestion] = useState(initial?.question ?? '');
  const [answer, setAnswer] = useState(initial?.answer ?? '');
  const [category, setCategory] = useState(initial?.category ?? '');
  const [isPublished, setIsPublished] = useState(initial?.isPublished ?? false);

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    onSave({
      question: question.trim(),
      answer: answer.trim(),
      category: category.trim(),
      isPublished,
    });
  };

  return (
    /* Backdrop */
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4">
      <Card className="w-full max-w-lg">
        <CardHeader>
          <CardTitle className="text-base">
            {initial != null ? 'Edit FAQ' : 'New FAQ'}
          </CardTitle>
        </CardHeader>
        <CardContent>
          <form onSubmit={handleSubmit} className="flex flex-col gap-4">
            <div>
              <label className="block text-sm font-medium text-gray-700">Question</label>
              <input
                required
                value={question}
                onChange={(e) => { setQuestion(e.target.value); }}
                className="mt-1 w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                placeholder="How do I track my order?"
              />
            </div>

            <div>
              <label className="block text-sm font-medium text-gray-700">Answer</label>
              <textarea
                required
                rows={5}
                value={answer}
                onChange={(e) => { setAnswer(e.target.value); }}
                className="mt-1 w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                placeholder="You can track your order by..."
              />
            </div>

            <div>
              <label className="block text-sm font-medium text-gray-700">Category</label>
              <input
                value={category}
                onChange={(e) => { setCategory(e.target.value); }}
                className="mt-1 w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                placeholder="Orders"
              />
            </div>

            <div className="flex items-center gap-3">
              <input
                id="faq-published"
                type="checkbox"
                checked={isPublished}
                onChange={(e) => { setIsPublished(e.target.checked); }}
                className="h-4 w-4 rounded border-gray-300 text-blue-600 focus:ring-blue-500"
              />
              <label htmlFor="faq-published" className="text-sm font-medium text-gray-700">
                Publish immediately
              </label>
            </div>

            <div className="flex justify-end gap-2 pt-2">
              <button
                type="button"
                onClick={onCancel}
                className="rounded-md border border-gray-300 px-4 py-2 text-sm text-gray-700 hover:bg-gray-50"
              >
                Cancel
              </button>
              <button
                type="submit"
                disabled={saving}
                className="rounded-md bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-700 disabled:cursor-not-allowed disabled:opacity-50"
              >
                {saving ? 'Saving…' : 'Save FAQ'}
              </button>
            </div>
          </form>
        </CardContent>
      </Card>
    </div>
  );
}

// ---------------------------------------------------------------------------
// Main page
// ---------------------------------------------------------------------------

export function FAQManagementPage() {
  const { token } = useAuthStore();
  const queryClient = useQueryClient();

  const [page, setPage] = useState(0);
  const [search, setSearch] = useState('');
  const [searchInput, setSearchInput] = useState('');
  const [showModal, setShowModal] = useState(false);
  const [editingFaq, setEditingFaq] = useState<FAQ | undefined>(undefined);

  const safeToken = token ?? '';

  const { data: faqPage, isLoading } = useQuery({
    queryKey: ['faqs', page, search],
    queryFn: () => listFaqs(safeToken, page, search),
    enabled: safeToken !== '',
  });

  const createMutation = useMutation({
    mutationFn: (req: CreateFAQRequest) => createFaq(safeToken, req),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['faqs'] });
      setShowModal(false);
      setEditingFaq(undefined);
    },
  });

  const updateMutation = useMutation({
    mutationFn: ({ id, req }: { id: string; req: Partial<CreateFAQRequest> }) =>
      updateFaq(safeToken, id, req),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['faqs'] });
      setShowModal(false);
      setEditingFaq(undefined);
    },
  });

  const deleteMutation = useMutation({
    mutationFn: (id: string) => deleteFaq(safeToken, id),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['faqs'] });
    },
  });

  const toggleMutation = useMutation({
    mutationFn: ({ id, isPublished }: { id: string; isPublished: boolean }) =>
      toggleFaqPublish(safeToken, id, isPublished),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['faqs'] });
    },
  });

  const handleSearch = (e: React.FormEvent) => {
    e.preventDefault();
    setSearch(searchInput.trim());
    setPage(0);
  };

  const handleDelete = (faq: FAQ) => {
    if (!window.confirm(`Delete FAQ "${faq.question}"? This cannot be undone.`)) return;
    deleteMutation.mutate(faq.id);
  };

  const handleEdit = (faq: FAQ) => {
    setEditingFaq(faq);
    setShowModal(true);
  };

  const handleSave = (req: CreateFAQRequest) => {
    if (editingFaq != null) {
      updateMutation.mutate({ id: editingFaq.id, req });
    } else {
      createMutation.mutate(req);
    }
  };

  const isSaving = createMutation.isPending || updateMutation.isPending;

  const faqs = faqPage?.content ?? [];
  const totalPages = faqPage?.totalPages ?? 1;

  return (
    <div className="flex flex-col gap-4">
      {/* Header */}
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h2 className="text-2xl font-bold text-gray-900">FAQ Management</h2>
          <p className="mt-1 text-sm text-gray-500">Manage frequently asked questions</p>
        </div>
        <button
          onClick={() => { setEditingFaq(undefined); setShowModal(true); }}
          className="rounded-md bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-700"
        >
          + New FAQ
        </button>
      </div>

      {/* Search */}
      <form onSubmit={handleSearch} className="flex items-center gap-2">
        <input
          type="search"
          value={searchInput}
          onChange={(e) => { setSearchInput(e.target.value); }}
          placeholder="Search FAQs…"
          className="w-full max-w-sm rounded-md border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
        />
        <button
          type="submit"
          className="rounded-md border border-gray-300 px-3 py-2 text-sm text-gray-700 hover:bg-gray-50"
        >
          Search
        </button>
        {search !== '' && (
          <button
            type="button"
            onClick={() => { setSearch(''); setSearchInput(''); setPage(0); }}
            className="text-sm text-gray-500 hover:underline"
          >
            Clear
          </button>
        )}
      </form>

      {/* Table */}
      {isLoading ? (
        <div className="flex h-48 items-center justify-center">
          <Spinner size="lg" label="Loading FAQs..." />
        </div>
      ) : (
        <div className="overflow-x-auto rounded-md border border-gray-200">
          <table className="min-w-full divide-y divide-gray-200 text-sm">
            <thead className="bg-gray-50">
              <tr>
                <th className="w-1/2 px-4 py-3 text-left font-medium text-gray-600">Question</th>
                <th className="px-4 py-3 text-left font-medium text-gray-600">Category</th>
                <th className="px-4 py-3 text-center font-medium text-gray-600">Status</th>
                <th className="px-4 py-3 text-right font-medium text-gray-600">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100 bg-white">
              {faqs.map((faq) => (
                <tr key={faq.id}>
                  <td className="max-w-xs px-4 py-3">
                    <p className="truncate font-medium text-gray-900">{faq.question}</p>
                    <p className="mt-0.5 truncate text-xs text-gray-400">{faq.answer}</p>
                  </td>
                  <td className="px-4 py-3 text-gray-600">{faq.category || '—'}</td>
                  <td className="px-4 py-3 text-center">
                    <Badge variant={faq.isPublished ? 'default' : 'secondary'}>
                      {faq.isPublished ? 'Published' : 'Draft'}
                    </Badge>
                  </td>
                  <td className="px-4 py-3 text-right">
                    <div className="flex items-center justify-end gap-2">
                      <button
                        onClick={() => {
                          toggleMutation.mutate({ id: faq.id, isPublished: !faq.isPublished });
                        }}
                        className="text-xs text-blue-600 hover:underline"
                      >
                        {faq.isPublished ? 'Unpublish' : 'Publish'}
                      </button>
                      <button
                        onClick={() => { handleEdit(faq); }}
                        className="text-xs text-gray-600 hover:underline"
                      >
                        Edit
                      </button>
                      <button
                        onClick={() => { handleDelete(faq); }}
                        className="text-xs text-red-500 hover:underline"
                      >
                        Delete
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
              {faqs.length === 0 && (
                <tr>
                  <td colSpan={4} className="px-4 py-6 text-center text-gray-400">
                    {search !== '' ? `No FAQs found for "${search}".` : 'No FAQs yet. Create one!'}
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      )}

      {/* Pagination */}
      {totalPages > 1 && (
        <div className="flex items-center justify-between text-sm text-gray-500">
          <span>
            Page {page + 1} of {totalPages} ({faqPage?.totalElements ?? 0} items)
          </span>
          <div className="flex gap-2">
            <button
              disabled={page === 0}
              onClick={() => { setPage((p) => p - 1); }}
              className="rounded-md border border-gray-300 px-3 py-1 text-xs disabled:opacity-40 hover:bg-gray-50"
            >
              Previous
            </button>
            <button
              disabled={page >= totalPages - 1}
              onClick={() => { setPage((p) => p + 1); }}
              className="rounded-md border border-gray-300 px-3 py-1 text-xs disabled:opacity-40 hover:bg-gray-50"
            >
              Next
            </button>
          </div>
        </div>
      )}

      {/* Create / edit modal */}
      {showModal && (
        <FaqModal
          initial={editingFaq}
          onSave={handleSave}
          onCancel={() => { setShowModal(false); setEditingFaq(undefined); }}
          saving={isSaving}
        />
      )}
    </div>
  );
}
