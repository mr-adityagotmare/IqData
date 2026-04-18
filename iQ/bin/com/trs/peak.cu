extern "C"
__global__ void peakKernel(
        float* spectrum,
        int N,
        int* maxIndex)
{
    int i = threadIdx.x;

    __shared__ float sdata[1024];
    __shared__ int sindex[1024];

    sdata[i] = spectrum[i];
    sindex[i] = i;

    __syncthreads();

    for(int s = blockDim.x/2; s>0; s>>=1)
    {
        if(i < s)
        {
            if(sdata[i] < sdata[i+s])
            {
                sdata[i] = sdata[i+s];
                sindex[i] = sindex[i+s];
            }
        }
        __syncthreads();
    }

    if(i==0)
        *maxIndex = sindex[0];
}