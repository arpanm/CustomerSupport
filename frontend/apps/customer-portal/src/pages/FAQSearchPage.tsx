import { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { Card, CardContent, CardHeader, CardTitle, Spinner } from '@supporthub/ui';
import { useAuthStore } from '../store/authStore.js';
import { getApiClient } from '../api/client.js';

function useDebounce(value: string, delayMs: number): string {
  const [debounced, setDebounced] = useState(value);

  useEffect(() => {
    const timer = setTimeout(() => {
      setDebounced(value);
    }, delayMs);
    return () => clearTimeout(timer);
  }, [value, delayMs]);

  return debounced;
}

function truncate(text: string, maxLength: number): string {
  if (text.length <= maxLength) return text;
  return `${text.slice(0, maxLength)}…`;
}

export function FAQSearchPage() {
  const [searchTerm, setSearchTerm] = useState('');
  const debouncedSearch = useDebounce(searchTerm, 400);
  const tenantId = useAuthStore((s) => s.tenantId) ?? '';
  const client = getApiClient(tenantId);

  const { data, isLoading, isError, error } = useQuery({
    queryKey: ['faq-search', debouncedSearch],
    queryFn: () => client.searchFaq(debouncedSearch),
    enabled: debouncedSearch.length > 2,
  });

  const results = data?.results ?? [];
  const hasSearched = debouncedSearch.length > 2;

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h2 className="text-2xl font-bold text-gray-900">FAQ Search</h2>
        <p className="mt-1 text-sm text-gray-600">
          Search our knowledge base for quick answers.
        </p>
      </div>

      <div className="relative">
        <input
          type="search"
          value={searchTerm}
          onChange={(e) => setSearchTerm(e.target.value)}
          placeholder="Search FAQs... (type at least 3 characters)"
          className="w-full rounded-lg border border-gray-300 bg-white px-4 py-3 pr-10 text-sm placeholder:text-gray-400 focus:border-blue-500 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-1"
          aria-label="Search FAQs"
        />
        {isLoading && (
          <div className="absolute right-3 top-1/2 -translate-y-1/2">
            <Spinner size="sm" label="Searching..." />
          </div>
        )}
      </div>

      {isError && (
        <Card>
          <CardContent className="py-6 text-center">
            <p className="text-sm text-red-600">
              {error instanceof Error ? error.message : 'Failed to search FAQs. Please try again.'}
            </p>
          </CardContent>
        </Card>
      )}

      {hasSearched && !isLoading && !isError && results.length === 0 && (
        <Card>
          <CardContent className="py-10 text-center">
            <p className="text-gray-500">No FAQs found. Try different keywords or create a support ticket.</p>
          </CardContent>
        </Card>
      )}

      {results.length > 0 && (
        <div className="flex flex-col gap-4">
          {results.map((faq) => (
            <Card key={faq.id}>
              <CardHeader>
                <CardTitle className="text-base font-semibold text-gray-900">
                  {faq.question}
                </CardTitle>
              </CardHeader>
              <CardContent className="flex flex-col gap-3">
                <p className="text-sm text-gray-700">
                  {truncate(faq.answerExcerpt, 200)}
                </p>
                <div className="flex items-center gap-2">
                  <div className="h-1.5 flex-1 rounded-full bg-gray-200">
                    <div
                      className="h-1.5 rounded-full bg-blue-500 transition-all"
                      style={{ width: `${Math.round(faq.score * 100)}%` }}
                    />
                  </div>
                  <span className="shrink-0 text-xs text-gray-500">
                    {Math.round(faq.score * 100)}% match
                  </span>
                </div>
              </CardContent>
            </Card>
          ))}
        </div>
      )}

      {!searchTerm && (
        <Card>
          <CardContent className="py-10 text-center">
            <p className="text-gray-500">Start typing to search our FAQ database.</p>
          </CardContent>
        </Card>
      )}

      <div className="flex justify-center border-t border-gray-200 pt-4">
        <p className="text-sm text-gray-600">
          Still need help?{' '}
          <Link to="/tickets/new" className="font-medium text-blue-600 hover:underline">
            Create a ticket
          </Link>
        </p>
      </div>
    </div>
  );
}
