extern "C"
__global__ void logKernel(
        float* mag,
        float* output,
        int N,
        float gain)
{
    int i = blockIdx.x * blockDim.x + threadIdx.x;

    if(i >= N) return;

    float v = mag[i];
    output[i] = 20.0f * log10f(v) + gain;
}