import { FormEvent, useCallback, useEffect, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import { useAuth } from "../lib/context/AuthContext";
import { authApi } from "../lib/api/auth";
import { ApiError } from "../lib/api/http";

function defaultPath(role: string) {
    if (role === "STUDENT") return "/student/dashboard";
    if (role === "LECTURER") return "/lecturer/dashboard";
    if (role === "ADMIN") return "/admin/dashboard";
    return "/";
}

export default function VerifyEmailPage() {
    const { user, refetch } = useAuth();
    const navigate = useNavigate();

    const [otp, setOtp] = useState(["", "", "", "", "", ""]);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState("");
    const [success, setSuccess] = useState("");
    const [resendCooldown, setResendCooldown] = useState(0);
    const inputRefs = useRef<(HTMLInputElement | null)[]>([]);

    // Redirect if already verified
    useEffect(() => {
        if (user?.emailVerified) {
            if (!user.profileComplete && user.role !== "ADMIN") {
                navigate("/onboarding", { replace: true });
            } else {
                navigate(defaultPath(user.role), { replace: true });
            }
        }
    }, [user, navigate]);

    // Redirect if not logged in
    useEffect(() => {
        if (!user) {
            const timer = setTimeout(() => {
                if (!user) navigate("/login", { replace: true });
            }, 2000);
            return () => clearTimeout(timer);
        }
    }, [user, navigate]);

    // Cooldown timer
    useEffect(() => {
        if (resendCooldown <= 0) return;
        const timer = setInterval(() => {
            setResendCooldown((prev) => Math.max(0, prev - 1));
        }, 1000);
        return () => clearInterval(timer);
    }, [resendCooldown]);

    // Focus first input on mount
    useEffect(() => {
        inputRefs.current[0]?.focus();
    }, []);

    const handleChange = (index: number, value: string) => {
        if (!/^\d*$/.test(value)) return; // Only digits

        const newOtp = [...otp];
        newOtp[index] = value.slice(-1); // Take last digit
        setOtp(newOtp);
        setError("");

        // Auto-focus next input
        if (value && index < 5) {
            inputRefs.current[index + 1]?.focus();
        }
    };

    const handleKeyDown = (index: number, e: React.KeyboardEvent) => {
        if (e.key === "Backspace" && !otp[index] && index > 0) {
            inputRefs.current[index - 1]?.focus();
        }
    };

    const handlePaste = (e: React.ClipboardEvent) => {
        e.preventDefault();
        const text = e.clipboardData.getData("text").replace(/\D/g, "").slice(0, 6);
        if (text.length === 0) return;
        const newOtp = [...otp];
        for (let i = 0; i < 6; i++) {
            newOtp[i] = text[i] || "";
        }
        setOtp(newOtp);
        const focusIndex = Math.min(text.length, 5);
        inputRefs.current[focusIndex]?.focus();
    };

    const submit = async (e: FormEvent) => {
        e.preventDefault();
        const code = otp.join("");
        if (code.length !== 6) {
            setError("Please enter the complete 6-digit code.");
            return;
        }

        setLoading(true);
        setError("");
        setSuccess("");

        try {
            await authApi.verifyOtp(code);
            setSuccess("Email verified successfully! Redirecting...");
            const updatedUser = await refetch();
            setTimeout(() => {
                if (updatedUser) {
                    if (!updatedUser.profileComplete && updatedUser.role !== "ADMIN") {
                        navigate("/onboarding", { replace: true });
                    } else {
                        navigate(defaultPath(updatedUser.role), { replace: true });
                    }
                }
            }, 1200);
        } catch (err: unknown) {
            if (err instanceof ApiError) {
                setError(err.message);
            } else {
                setError("Verification failed. Please try again.");
            }
        } finally {
            setLoading(false);
        }
    };

    const resendOtp = useCallback(async () => {
        if (resendCooldown > 0) return;
        setError("");
        setSuccess("");
        try {
            const result = await authApi.sendOtp();
            setSuccess(result.message);
            setResendCooldown(60);
            setOtp(["", "", "", "", "", ""]);
            inputRefs.current[0]?.focus();
        } catch (err: unknown) {
            if (err instanceof ApiError) {
                setError(err.message);
            } else {
                setError("Failed to resend code.");
            }
        }
    }, [resendCooldown]);

    if (!user) {
        return (
            <div className="auth-shell">
                <div className="auth-card text-center">
                    <div className="su-spinner mx-auto" style={{ width: "2.5rem", height: "2.5rem" }} />
                    <p className="text-muted mt-3">Loading...</p>
                </div>
            </div>
        );
    }

    return (
        <div className="auth-shell">
            <form className="auth-card" onSubmit={submit}>
                <div className="text-center mb-4">
                    {/* Email Icon */}
                    <div
                        className="mx-auto mb-3 d-flex align-items-center justify-content-center"
                        style={{
                            width: "4rem",
                            height: "4rem",
                            borderRadius: "50%",
                            background: "linear-gradient(135deg, #1a3a5c 0%, #2d5f8a 100%)",
                            boxShadow: "0 4px 16px rgba(26,58,92,0.25)",
                        }}
                    >
                        <span style={{ fontSize: "1.6rem" }}>✉️</span>
                    </div>
                    <h1 className="h5 su-page-title mb-1">Email Verification</h1>
                    <p className="text-muted small mb-0">
                        We've sent a 6-digit verification code to
                    </p>
                    <p className="fw-semibold small" style={{ color: "#1a3a5c" }}>
                        {user.email}
                    </p>
                </div>

                {/* OTP Inputs */}
                <div className="d-flex justify-content-center gap-2 mb-4" onPaste={handlePaste}>
                    {otp.map((digit, idx) => (
                        <input
                            key={idx}
                            ref={(el) => { inputRefs.current[idx] = el; }}
                            type="text"
                            inputMode="numeric"
                            maxLength={1}
                            value={digit}
                            onChange={(e) => handleChange(idx, e.target.value)}
                            onKeyDown={(e) => handleKeyDown(idx, e)}
                            className="form-control text-center fw-bold"
                            style={{
                                width: "3rem",
                                height: "3.5rem",
                                fontSize: "1.4rem",
                                borderRadius: "0.6rem",
                                border: `2px solid ${digit ? "#1a3a5c" : "#d5e3ed"}`,
                                transition: "border-color 0.2s ease",
                                caretColor: "#1a3a5c",
                            }}
                            autoComplete="one-time-code"
                        />
                    ))}
                </div>

                {/* Messages */}
                {error && (
                    <div className="alert alert-danger py-2 d-flex align-items-center gap-2 mb-3" style={{ borderRadius: "0.6rem" }}>
                        <span>⚠️</span> {error}
                    </div>
                )}

                {success && (
                    <div className="alert alert-success py-2 d-flex align-items-center gap-2 mb-3" style={{ borderRadius: "0.6rem" }}>
                        <span>✅</span> {success}
                    </div>
                )}

                {/* Submit */}
                <div className="d-grid gap-2">
                    <button
                        className="btn btn-primary btn-lg"
                        disabled={loading || otp.join("").length !== 6}
                        type="submit"
                        style={{ fontSize: "0.95rem", borderRadius: "0.6rem", padding: "0.7rem" }}
                    >
                        {loading ? (
                            <>
                                <span
                                    className="su-spinner d-inline-block me-2"
                                    style={{ width: "1rem", height: "1rem", borderWidth: 2, borderTopColor: "#fff" }}
                                />{" "}
                                Verifying...
                            </>
                        ) : (
                            "Verify Email"
                        )}
                    </button>

                    {/* Resend */}
                    <div className="text-center mt-2">
                        <p className="text-muted small mb-1">Didn't receive the code?</p>
                        <button
                            className="btn btn-link btn-sm p-0"
                            type="button"
                            onClick={resendOtp}
                            disabled={resendCooldown > 0}
                            style={{ color: resendCooldown > 0 ? "#aaa" : "#1a3a5c" }}
                        >
                            {resendCooldown > 0
                                ? `Resend code in ${resendCooldown}s`
                                : "Resend verification code"}
                        </button>
                    </div>

                    <hr className="my-3" />

                    <div className="text-center">
                        <button
                            className="btn btn-link btn-sm text-muted"
                            type="button"
                            onClick={() => navigate("/login")}
                        >
                            ← Back to Sign In
                        </button>
                    </div>
                </div>
            </form>
        </div>
    );
}
