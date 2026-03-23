import { useForm, type SubmitHandler } from 'react-hook-form';
import { useNavigate } from 'react-router-dom';
import { Button, Card, CardContent, CardHeader, CardTitle, Input } from '@supporthub/ui';

interface CreateTicketFormValues {
  title: string;
  description: string;
  channel: string;
}

export function CreateTicketPage() {
  const navigate = useNavigate();

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<CreateTicketFormValues>({
    defaultValues: {
      channel: 'web',
    },
  });

  const onSubmit: SubmitHandler<CreateTicketFormValues> = async (_data) => {
    // TODO: integrate with SupportHubClient once auth is wired up
    // const client = new SupportHubClient({ baseUrl: import.meta.env.VITE_API_BASE_URL, tenantId: import.meta.env.VITE_TENANT_ID });
    // await client.createTicket({ ...data, categoryId: selectedCategoryId });
    await navigate('/');
  };

  return (
    <Card className="mx-auto max-w-xl">
      <CardHeader>
        <CardTitle>Create Support Ticket</CardTitle>
      </CardHeader>
      <CardContent>
        <form
          onSubmit={(e) => {
            void handleSubmit(onSubmit)(e);
          }}
          className="flex flex-col gap-4"
          noValidate
        >
          <Input
            label="Subject"
            placeholder="Briefly describe your issue"
            error={errors.title?.message}
            {...register('title', {
              required: 'Subject is required',
              minLength: { value: 10, message: 'Subject must be at least 10 characters' },
              maxLength: { value: 200, message: 'Subject must be at most 200 characters' },
            })}
          />

          <div className="flex flex-col gap-1">
            <label
              htmlFor="description"
              className="text-sm font-medium leading-none text-gray-700"
            >
              Description
            </label>
            <textarea
              id="description"
              rows={5}
              placeholder="Describe your issue in detail..."
              className="flex w-full rounded-md border border-gray-300 bg-white px-3 py-2 text-sm placeholder:text-gray-400 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-600 focus-visible:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-50"
              aria-invalid={errors.description !== undefined}
              {...register('description', {
                required: 'Description is required',
                minLength: { value: 20, message: 'Description must be at least 20 characters' },
                maxLength: {
                  value: 5000,
                  message: 'Description must be at most 5000 characters',
                },
              })}
            />
            {errors.description !== undefined && (
              <p className="text-xs text-red-600" role="alert">
                {errors.description.message}
              </p>
            )}
          </div>

          <div className="flex gap-3 pt-2">
            <Button type="submit" isLoading={isSubmitting} className="flex-1">
              Submit Ticket
            </Button>
            <Button
              type="button"
              variant="outline"
              onClick={() => {
                void navigate('/');
              }}
            >
              Cancel
            </Button>
          </div>
        </form>
      </CardContent>
    </Card>
  );
}
