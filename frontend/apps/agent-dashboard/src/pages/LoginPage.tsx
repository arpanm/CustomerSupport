import { useForm, type SubmitHandler } from 'react-hook-form';
import { useNavigate } from 'react-router-dom';
import { Button, Card, CardContent, CardHeader, CardTitle, CardDescription, Input } from '@supporthub/ui';
import { useAuthStore } from '../store/authStore.js';

interface LoginFormValues {
  email: string;
  password: string;
}

export function LoginPage() {
  const navigate = useNavigate();
  const login = useAuthStore((state) => state.login);

  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<LoginFormValues>();

  const onSubmit: SubmitHandler<LoginFormValues> = async (data) => {
    try {
      // TODO: replace with real auth-service API call
      const response = await fetch(`${import.meta.env.VITE_API_BASE_URL}/api/v1/auth/agent/login`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'X-Tenant-ID': import.meta.env.VITE_TENANT_ID,
        },
        body: JSON.stringify({ email: data.email, password: data.password }),
      });

      if (!response.ok) {
        setError('root', { message: 'Invalid email or password' });
        return;
      }

      const result = await response.json() as {
        data: {
          token: string;
          user: {
            id: string;
            name: string;
            email: string;
            role: 'AGENT' | 'SENIOR_AGENT' | 'ADMIN' | 'SUPER_ADMIN';
            tenantId: string;
          };
        };
      };

      login(result.data.user, result.data.token);
      navigate('/');
    } catch {
      setError('root', { message: 'Network error. Please try again.' });
    }
  };

  return (
    <div className="flex min-h-screen items-center justify-center bg-gray-50 px-4">
      <Card className="w-full max-w-md">
        <CardHeader className="text-center">
          <CardTitle className="text-2xl font-bold text-blue-600">SupportHub</CardTitle>
          <CardDescription>Sign in to your agent account</CardDescription>
        </CardHeader>
        <CardContent>
          <form
            onSubmit={(e) => {
              void handleSubmit(onSubmit)(e);
            }}
            className="flex flex-col gap-4"
            noValidate
          >
            {errors.root !== undefined && (
              <div
                className="rounded-md border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700"
                role="alert"
              >
                {errors.root.message}
              </div>
            )}

            <Input
              label="Email"
              type="email"
              placeholder="agent@company.com"
              autoComplete="email"
              error={errors.email?.message}
              {...register('email', {
                required: 'Email is required',
                pattern: {
                  value: /^[^\s@]+@[^\s@]+\.[^\s@]+$/,
                  message: 'Enter a valid email address',
                },
              })}
            />

            <Input
              label="Password"
              type="password"
              placeholder="••••••••"
              autoComplete="current-password"
              error={errors.password?.message}
              {...register('password', {
                required: 'Password is required',
                minLength: { value: 8, message: 'Password must be at least 8 characters' },
              })}
            />

            <Button type="submit" isLoading={isSubmitting} className="mt-2 w-full">
              Sign In
            </Button>
          </form>
        </CardContent>
      </Card>
    </div>
  );
}
