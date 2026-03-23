import { Link } from 'react-router-dom';
import { Button, Card, CardContent, CardHeader, CardTitle, CardDescription } from '@supporthub/ui';

export function HomePage() {
  return (
    <div className="flex flex-col items-center gap-8">
      <div className="text-center">
        <h2 className="text-3xl font-bold text-gray-900">How can we help you?</h2>
        <p className="mt-2 text-gray-600">
          Create a support ticket or browse our FAQs for quick answers.
        </p>
      </div>

      <div className="grid w-full max-w-2xl gap-4 sm:grid-cols-2">
        <Card>
          <CardHeader>
            <CardTitle className="text-lg">Submit a Ticket</CardTitle>
            <CardDescription>
              Describe your issue and our team will get back to you.
            </CardDescription>
          </CardHeader>
          <CardContent>
            <Link to="/tickets/new">
              <Button className="w-full">Create Ticket</Button>
            </Link>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle className="text-lg">Browse FAQs</CardTitle>
            <CardDescription>
              Find answers to frequently asked questions instantly.
            </CardDescription>
          </CardHeader>
          <CardContent>
            <Link to="/faqs">
              <Button variant="outline" className="w-full">
                View FAQs
              </Button>
            </Link>
          </CardContent>
        </Card>
      </div>
    </div>
  );
}
