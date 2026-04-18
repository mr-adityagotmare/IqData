extern "C"
__global__ void slidingWindowKernel(int *input, int *output, int N, int windowSize, int step)
{
    int windowId = blockIdx.x;

    int start = windowId * step;

    if(start + windowSize > N)
        return;

    int tid = threadIdx.x;

    for(int i = tid; i < windowSize; i += blockDim.x)
    {
        int index = start + i;

        // Example processing (copy)
        output[windowId * windowSize + i] = input[index];
    }
}
