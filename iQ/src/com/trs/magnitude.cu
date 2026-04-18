extern "C"
__global__ void magnitudeKernel(
        float* fft,
        float* mag,
        int N)
{
    int i = blockIdx.x * blockDim.x + threadIdx.x;

    if(i >= N) return;

    float re = fft[2*i];
    float im = fft[2*i+1];

    mag[i] = sqrtf(re*re + im*im);
    
}