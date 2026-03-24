import { useQuery, useQueryClient } from '@tanstack/react-query';
import { Card, CardContent, CardHeader, CardTitle, Spinner, Badge } from '@supporthub/ui';

// ---------------------------------------------------------------------------
// Types
// ---------------------------------------------------------------------------

interface ResolutionSuggestion {
  id: string;
  title: string;
  content: string;
  /** 0.0 – 1.0 */
  confidence: number;
}

type SentimentLevel = 'POSITIVE' | 'NEUTRAL' | 'NEGATIVE' | 'FRUSTRATED';

interface AIAssistancePanelProps {
  ticketId: string;
  /** JWT bearer token */
  token: string;
  /** Optional sentiment label from ticket data */
  sentiment?: string;
  /** Called when the agent clicks "Apply as reply" */
  onApply: (text: string) => void;
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

async function fetchSuggestions(token: string, ticketId: string): Promise<ResolutionSuggestion[]> {
  const res = await fetch(
    `/api/v1/ai/resolution-suggestions?ticketId=${encodeURIComponent(ticketId)}`,
    {
      headers: {
        Authorization: `Bearer ${token}`,
        'Content-Type': 'application/json',
      },
    },
  );
  if (!res.ok) {
    throw new Error(`Failed to fetch AI suggestions: ${res.status}`);
  }
  return res.json() as Promise<ResolutionSuggestion[]>;
}

const SENTIMENT_VARIANT: Record<SentimentLevel, 'default' | 'destructive' | 'secondary' | 'outline'> = {
  POSITIVE: 'default',
  NEUTRAL: 'secondary',
  NEGATIVE: 'destructive',
  FRUSTRATED: 'destructive',
};

const SENTIMENT_LABEL: Record<SentimentLevel, string> = {
  POSITIVE: 'Positive',
  NEUTRAL: 'Neutral',
  NEGATIVE: 'Negative',
  FRUSTRATED: 'Frustrated',
};

function isSentimentLevel(value: string): value is SentimentLevel {
  return ['POSITIVE', 'NEUTRAL', 'NEGATIVE', 'FRUSTRATED'].includes(value);
}

// ---------------------------------------------------------------------------
// Component
// ---------------------------------------------------------------------------

export function AIAssistancePanel({ ticketId, token, sentiment, onApply }: AIAssistancePanelProps) {
  const queryClient = useQueryClient();
  const queryKey = ['ai-suggestions', ticketId];

  const { data: suggestions, isLoading, isError, error } = useQuery<ResolutionSuggestion[], Error>({
    queryKey,
    queryFn: () => fetchSuggestions(token, ticketId),
    staleTime: 5 * 60_000,
    retry: 1,
  });

  const handleRefresh = () => {
    void queryClient.invalidateQueries({ queryKey });
  };

  const normalizedSentiment = sentiment != null ? sentiment.toUpperCase() : undefined;

  return (
    <Card className="h-fit">
      <CardHeader>
        <div className="flex items-center justify-between">
          <CardTitle className="flex items-center gap-2 text-sm">
            AI Assistance
          </CardTitle>
          <button
            onClick={handleRefresh}
            disabled={isLoading}
            className="rounded-md border border-gray-200 px-2 py-1 text-xs text-gray-600 hover:bg-gray-50 disabled:opacity-50"
          >
            Refresh
          </button>
        </div>

        {/* Sentiment badge */}
        {normalizedSentiment != null && isSentimentLevel(normalizedSentiment) && (
          <div className="mt-2 flex items-center gap-2 text-xs text-gray-500">
            <span>Customer sentiment:</span>
            <Badge variant={SENTIMENT_VARIANT[normalizedSentiment]}>
              {SENTIMENT_LABEL[normalizedSentiment]}
            </Badge>
          </div>
        )}
      </CardHeader>

      <CardContent>
        {isLoading && (
          <div className="flex items-center justify-center py-6">
            <Spinner size="sm" label="Generating suggestions..." />
          </div>
        )}

        {!isLoading && isError && (
          <div className="rounded-md border border-red-200 bg-red-50 px-3 py-2 text-xs text-red-700">
            {error.message}
          </div>
        )}

        {!isLoading && !isError && (suggestions == null || suggestions.length === 0) && (
          <p className="text-center text-xs text-gray-500">No suggestions available.</p>
        )}

        {!isLoading && !isError && suggestions != null && suggestions.length > 0 && (
          <div className="flex flex-col gap-3">
            <p className="text-xs text-gray-500">
              Click a suggestion to use it as a reply:
            </p>
            {suggestions.map((s) => {
              const pct = Math.round(s.confidence * 100);
              return (
                <div
                  key={s.id}
                  className="rounded-md border border-gray-200 bg-gray-50 p-3"
                >
                  <div className="flex items-start justify-between gap-2">
                    <span className="text-xs font-semibold text-gray-900">{s.title}</span>
                    <span className="shrink-0 rounded-full bg-green-100 px-2 py-0.5 text-xs font-medium text-green-700">
                      {pct}%
                    </span>
                  </div>

                  {/* Confidence progress bar */}
                  <div className="mt-1.5 h-1.5 w-full rounded-full bg-gray-200">
                    <div
                      className="h-1.5 rounded-full bg-green-500"
                      style={{ width: `${pct}%` }}
                    />
                  </div>

                  <p className="mt-2 line-clamp-3 text-xs text-gray-600">{s.content}</p>

                  <button
                    onClick={() => { onApply(s.content); }}
                    className="mt-2 rounded-md bg-blue-600 px-3 py-1 text-xs font-medium text-white hover:bg-blue-700"
                  >
                    Apply as reply
                  </button>
                </div>
              );
            })}
          </div>
        )}
      </CardContent>
    </Card>
  );
}
