import { Card, CardContent, CardHeader, CardTitle } from '@supporthub/ui';

interface StatCardProps {
  title: string;
  value: string;
  description: string;
}

function StatCard({ title, value, description }: StatCardProps) {
  return (
    <Card>
      <CardHeader className="pb-2">
        <CardTitle className="text-sm font-medium text-gray-500">{title}</CardTitle>
      </CardHeader>
      <CardContent>
        <p className="text-3xl font-bold text-gray-900">{value}</p>
        <p className="mt-1 text-xs text-gray-500">{description}</p>
      </CardContent>
    </Card>
  );
}

export function DashboardPage() {
  return (
    <div className="flex flex-col gap-6">
      <div>
        <h2 className="text-2xl font-bold text-gray-900">Dashboard</h2>
        <p className="mt-1 text-sm text-gray-500">
          Overview of your support operations
        </p>
      </div>

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <StatCard
          title="Open Tickets"
          value="—"
          description="Awaiting agent assignment"
        />
        <StatCard
          title="In Progress"
          value="—"
          description="Currently being worked on"
        />
        <StatCard
          title="Resolved Today"
          value="—"
          description="Closed in the last 24 hours"
        />
        <StatCard
          title="Avg. Resolution Time"
          value="—"
          description="Last 7 days"
        />
      </div>

      <Card>
        <CardHeader>
          <CardTitle>Recent Activity</CardTitle>
        </CardHeader>
        <CardContent>
          <p className="text-sm text-gray-500">
            Reporting and activity data will appear here once connected to the reporting service.
          </p>
        </CardContent>
      </Card>
    </div>
  );
}
