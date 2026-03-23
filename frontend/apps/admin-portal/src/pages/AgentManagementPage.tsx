import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { Card, CardContent, Spinner, Badge } from '@supporthub/ui';
import { useAuthStore } from '../store/authStore.js';
import { listAgents, createAgent } from '../api/adminApi.js';
import type { CreateAgentRequest } from '../api/adminApi.js';

const ROLE_OPTIONS: CreateAgentRequest['role'][] = ['AGENT', 'SENIOR_AGENT', 'ADMIN'];
const ROLE_VARIANT: Record<string, 'default' | 'secondary' | 'destructive'> = {
  SUPER_ADMIN: 'destructive',
  ADMIN: 'default',
  SENIOR_AGENT: 'default',
  AGENT: 'secondary',
};

function AgentForm({
  onSave,
  onCancel,
}: {
  onSave: (r: CreateAgentRequest) => void;
  onCancel: () => void;
}) {
  const [form, setForm] = useState<CreateAgentRequest>({
    displayName: '',
    email: '',
    password: '',
    role: 'AGENT',
  });

  const update = (field: keyof CreateAgentRequest, value: string) => {
    setForm((f) => ({ ...f, [field]: value }));
  };

  return (
    <form
      onSubmit={(e) => {
        e.preventDefault();
        onSave(form);
      }}
      className="flex flex-col gap-4"
    >
      <div className="grid grid-cols-2 gap-4">
        <div>
          <label className="block text-sm font-medium text-gray-700">Display Name</label>
          <input
            required
            value={form.displayName}
            onChange={(e) => { update('displayName', e.target.value); }}
            className="mt-1 w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
            placeholder="Rahul Sharma"
          />
        </div>
        <div>
          <label className="block text-sm font-medium text-gray-700">Email</label>
          <input
            required
            type="email"
            value={form.email}
            onChange={(e) => { update('email', e.target.value); }}
            className="mt-1 w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
            placeholder="rahul@company.in"
          />
        </div>
        <div>
          <label className="block text-sm font-medium text-gray-700">Temporary Password</label>
          <input
            required
            type="password"
            minLength={8}
            value={form.password}
            onChange={(e) => { update('password', e.target.value); }}
            className="mt-1 w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
            placeholder="Min. 8 characters"
          />
        </div>
        <div>
          <label className="block text-sm font-medium text-gray-700">Role</label>
          <select
            value={form.role}
            onChange={(e) => { update('role', e.target.value as CreateAgentRequest['role']); }}
            className="mt-1 w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
          >
            {ROLE_OPTIONS.map((r) => (
              <option key={r} value={r}>{r.replace(/_/g, ' ')}</option>
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
          Create Agent
        </button>
      </div>
    </form>
  );
}

export function AgentManagementPage() {
  const { token } = useAuthStore();
  const queryClient = useQueryClient();
  const [showForm, setShowForm] = useState(false);

  const { data: agents, isLoading } = useQuery({
    queryKey: ['agents'],
    queryFn: () => listAgents(token ?? ''),
    enabled: token != null,
  });

  const createMutation = useMutation({
    mutationFn: (req: CreateAgentRequest) => createAgent(token ?? '', req),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['agents'] });
      setShowForm(false);
    },
  });

  return (
    <div className="flex flex-col gap-4">
      <div className="flex items-center justify-between">
        <h2 className="text-2xl font-bold text-gray-900">Agent Management</h2>
        <button
          onClick={() => { setShowForm(true); }}
          className="rounded-md bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-700"
        >
          + Add Agent
        </button>
      </div>

      {showForm && (
        <Card>
          <CardContent className="py-4">
            <h3 className="mb-4 font-medium text-gray-900">New Agent Account</h3>
            <AgentForm
              onSave={(req) => { createMutation.mutate(req); }}
              onCancel={() => { setShowForm(false); }}
            />
          </CardContent>
        </Card>
      )}

      {isLoading ? (
        <div className="flex h-32 items-center justify-center">
          <Spinner size="lg" label="Loading agents..." />
        </div>
      ) : (
        <div className="overflow-hidden rounded-lg border border-gray-200 bg-white">
          <table className="w-full text-sm">
            <thead className="border-b border-gray-200 bg-gray-50">
              <tr>
                <th className="px-4 py-3 text-left font-medium text-gray-700">Name</th>
                <th className="px-4 py-3 text-left font-medium text-gray-700">Email</th>
                <th className="px-4 py-3 text-left font-medium text-gray-700">Role</th>
                <th className="px-4 py-3 text-left font-medium text-gray-700">Status</th>
                <th className="px-4 py-3 text-left font-medium text-gray-700">Joined</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100">
              {(agents ?? []).map((agent) => (
                <tr key={agent.id} className="hover:bg-gray-50">
                  <td className="px-4 py-3 font-medium text-gray-900">{agent.displayName}</td>
                  <td className="px-4 py-3 text-gray-600">{agent.email}</td>
                  <td className="px-4 py-3">
                    <Badge variant={ROLE_VARIANT[agent.role] ?? 'secondary'}>
                      {agent.role.replace(/_/g, ' ')}
                    </Badge>
                  </td>
                  <td className="px-4 py-3">
                    <span
                      className={`inline-flex items-center gap-1 text-xs ${
                        agent.isActive ? 'text-green-600' : 'text-gray-400'
                      }`}
                    >
                      <span
                        className={`h-1.5 w-1.5 rounded-full ${
                          agent.isActive ? 'bg-green-500' : 'bg-gray-300'
                        }`}
                      />
                      {agent.isAvailable ? 'Available' : agent.isActive ? 'Away' : 'Inactive'}
                    </span>
                  </td>
                  <td className="px-4 py-3 text-gray-500">
                    {new Date(agent.createdAt).toLocaleDateString('en-IN')}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
