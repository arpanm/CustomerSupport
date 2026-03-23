import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useForm, type SubmitHandler } from 'react-hook-form';
import { Button, Card, CardContent, CardHeader, CardTitle, Input, Spinner } from '@supporthub/ui';
import { useAuthStore } from '../store/authStore.js';

interface PhoneFormValues {
  phone: string;
}

interface OtpFormValues {
  otp: string;
}

interface OtpSendResponse {
  sessionId: string;
}

interface OtpVerifyResponse {
  token: string;
  customerId: string;
  tenantId: string;
}

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL as string;
const TENANT_ID = import.meta.env.VITE_TENANT_ID as string;

export function LoginPage() {
  const navigate = useNavigate();
  const setAuth = useAuthStore((s) => s.setAuth);
  const [step, setStep] = useState<'phone' | 'otp'>('phone');
  const [phone, setPhone] = useState('');
  const [sessionId, setSessionId] = useState('');
  const [sendError, setSendError] = useState<string | null>(null);
  const [verifyError, setVerifyError] = useState<string | null>(null);

  const phoneForm = useForm<PhoneFormValues>();
  const otpForm = useForm<OtpFormValues>();

  const onSendOtp: SubmitHandler<PhoneFormValues> = async (data) => {
    setSendError(null);
    try {
      const response = await fetch(`${API_BASE_URL}/api/v1/auth/otp/send`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'X-Tenant-ID': TENANT_ID,
        },
        body: JSON.stringify({ phone: data.phone }),
      });

      if (!response.ok) {
        const errorBody: { error?: { message?: string } } = await response.json().catch(() => ({}));
        throw new Error(errorBody.error?.message ?? `Request failed with status ${response.status}`);
      }

      const body: OtpSendResponse = await response.json();
      setPhone(data.phone);
      setSessionId(body.sessionId);
      setStep('otp');
    } catch (err) {
      setSendError(err instanceof Error ? err.message : 'Failed to send OTP. Please try again.');
    }
  };

  const onVerifyOtp: SubmitHandler<OtpFormValues> = async (data) => {
    setVerifyError(null);
    try {
      const response = await fetch(`${API_BASE_URL}/api/v1/auth/otp/verify`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'X-Tenant-ID': TENANT_ID,
        },
        body: JSON.stringify({ phone, otp: data.otp, sessionId }),
      });

      if (!response.ok) {
        const errorBody: { error?: { message?: string } } = await response.json().catch(() => ({}));
        throw new Error(errorBody.error?.message ?? `Request failed with status ${response.status}`);
      }

      const body: OtpVerifyResponse = await response.json();
      setAuth(body.token, body.customerId, body.tenantId);
      await navigate('/tickets');
    } catch (err) {
      setVerifyError(err instanceof Error ? err.message : 'Invalid OTP. Please try again.');
    }
  };

  return (
    <div className="flex min-h-screen items-center justify-center bg-gray-50 px-4">
      <Card className="w-full max-w-sm">
        <CardHeader>
          <CardTitle className="text-center text-2xl">Welcome to SupportHub</CardTitle>
        </CardHeader>
        <CardContent>
          {step === 'phone' ? (
            <form
              onSubmit={(e) => {
                void phoneForm.handleSubmit(onSendOtp)(e);
              }}
              className="flex flex-col gap-4"
              noValidate
            >
              <p className="text-sm text-gray-600">
                Enter your phone number to receive a one-time password.
              </p>
              <Input
                label="Phone Number"
                type="tel"
                placeholder="+91 9876543210"
                error={phoneForm.formState.errors.phone?.message}
                {...phoneForm.register('phone', {
                  required: 'Phone number is required',
                  pattern: {
                    value: /^\+?[0-9]{10,15}$/,
                    message: 'Enter a valid phone number',
                  },
                })}
              />
              {sendError !== null && (
                <p className="text-sm text-red-600" role="alert">
                  {sendError}
                </p>
              )}
              <Button
                type="submit"
                className="w-full"
                disabled={phoneForm.formState.isSubmitting}
              >
                {phoneForm.formState.isSubmitting ? (
                  <Spinner size="sm" label="Sending OTP..." />
                ) : (
                  'Send OTP'
                )}
              </Button>
            </form>
          ) : (
            <form
              onSubmit={(e) => {
                void otpForm.handleSubmit(onVerifyOtp)(e);
              }}
              className="flex flex-col gap-4"
              noValidate
            >
              <p className="text-sm text-gray-600">
                Enter the 6-digit OTP sent to <span className="font-medium">{phone}</span>.
              </p>
              <Input
                label="One-Time Password"
                type="text"
                inputMode="numeric"
                placeholder="123456"
                maxLength={6}
                error={otpForm.formState.errors.otp?.message}
                {...otpForm.register('otp', {
                  required: 'OTP is required',
                  pattern: {
                    value: /^[0-9]{6}$/,
                    message: 'OTP must be exactly 6 digits',
                  },
                })}
              />
              {verifyError !== null && (
                <p className="text-sm text-red-600" role="alert">
                  {verifyError}
                </p>
              )}
              <Button
                type="submit"
                className="w-full"
                disabled={otpForm.formState.isSubmitting}
              >
                {otpForm.formState.isSubmitting ? (
                  <Spinner size="sm" label="Verifying..." />
                ) : (
                  'Verify OTP'
                )}
              </Button>
              <Button
                type="button"
                variant="ghost"
                className="w-full text-sm"
                onClick={() => {
                  setStep('phone');
                  setVerifyError(null);
                  otpForm.reset();
                }}
              >
                Change phone number
              </Button>
            </form>
          )}
        </CardContent>
      </Card>
    </div>
  );
}
