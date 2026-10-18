package com.example.procurement.supplier.config;


import java.util.*;


// Definition for singly-linked list.
class ListNode {
    int val;
    ListNode next;
    ListNode() {}
    ListNode(int val) { this.val = val; }
    ListNode(int val, ListNode next) { this.val = val; this.next = next; }
}

// Definition for a binary tree node.
class TreeNode {
    int val;
    TreeNode left;
    TreeNode right;
    TreeNode() {}
    TreeNode(int val) { this.val = val; }
    TreeNode(int val, TreeNode left, TreeNode right) {
        this.val = val;
        this.left = left;
        this.right = right;
    }
}

class Solution {

     public TreeNode invertTree(TreeNode root) {
        if(root == null){
            return null;
        }

        TreeNode left = invertTree(root.left);
        TreeNode right = invertTree(root.right);

        root.left = right;
        root.right = left;
        return root;
    }

    public boolean isMirror(TreeNode left, TreeNode right){
        if(left == null && right == null){
            return true;
        }
        if(left == null || right == null){
            return false;
        }
        return left.val == right.val && isMirror(left.left, right.right) && isMirror(left.right, right.left);
    }

    public boolean isSymmetric(TreeNode root) {

         return isMirror(root.left, root.right);
    }

    private int ans = 0;

    public int diameter(TreeNode root) {
         depth(root);
         return ans;
    }

    public int depth(TreeNode root) {
        if(root == null){
            return 0;
        }

        int left = depth(root.left);
        int right = depth(root.right);
        ans = Math.max(ans, left + right);
        return Math.max(left, right) + 1;
    }

    public List<List<Integer>> levelOrder(TreeNode root) {
        List<List<Integer>> res = new ArrayList<>();
        if(root == null){
            return res;
        }

        Queue<TreeNode> queue = new LinkedList<>();
        queue.add(root);

        while(!queue.isEmpty()){
            int size = queue.size();
            List<Integer> level = new ArrayList<>(size);
            while(size > 0){
                TreeNode node = queue.poll();
                level.add(node.val);
                if(node.left != null){
                    queue.add(node.left);
                }
                if(node.right != null){
                    queue.add(node.right);
                }
                size--;
            }
            res.add(level);
        }

        return res;
    }

    public TreeNode sortedArrayToBST(int[] nums) {
        return helper(nums,0,nums.length -1);
    }


    public TreeNode helper(int[] nums, int left, int right) {
        if(left > right){
            return null;
        }

        int mid = (left + right)/2;

        TreeNode root = new TreeNode(nums[mid]);
        root.left = helper(nums, left, mid - 1);
        root.right = helper(nums, mid + 1, right);
        return root;

    }

    public List<Integer> rightSideView(TreeNode root) {

        List<Integer> res = new ArrayList<>();
        if(root == null) return res;

        Deque<TreeNode> queue = new ArrayDeque<>();
        queue.add(root);

        while(!queue.isEmpty()){
            int size = queue.size();
            List<Integer> level = new ArrayList<>(size);
            for(int i =0;i<size;i++){
                TreeNode node =queue.poll();
                level.add(node.val);
                if(node.left != null) queue.add(node.left);
                if(node.right != null) queue.add(node.right);
            }
            res.add(level.get(level.size()-1));
        }
        return res;
    }

    public Map<Integer, Integer> indexMap;

    public TreeNode hepler(int[] preorder, int[] inorder,
                           int preorder_left, int preorder_right,
                           int inorder_left, int inorder_right) {

        if(preorder_left > preorder_right){
            return null;
        }

        int preorder_root = preorder_left;
        int inorder_root = indexMap.get(preorder[preorder_root]);

        TreeNode root = new TreeNode(preorder[preorder_root]);
        int size_left_subtree = inorder_root - inorder_left;

        root.left = hepler(preorder,inorder,
                preorder_left+1,preorder_left + size_left_subtree,
                inorder_left, inorder_root - 1);

        root.right = hepler(preorder, inorder,
                preorder_left + size_left_subtree + 1, preorder_right,
                inorder_root + 1, inorder_right);
        return root;
    }

    public TreeNode buildTree(int[] preorder, int[] inorder) {
        int n = preorder.length;
        // 构造哈希映射，帮助我们快速定位根节点
        indexMap = new HashMap<Integer, Integer>();
        for (int i = 0; i < n; i++) {
            indexMap.put(inorder[i], i);
        }
        return hepler(preorder, inorder, 0, n - 1, 0, n - 1);
    }

    public int rootSum(TreeNode root, int targetSum) {
        int ret = 0;

        if(root == null){
            return 0;
        }

        int val = root.val;
        if(val == targetSum){
            ret++;
        }

        ret += rootSum(root.left, targetSum - val);
        ret += rootSum(root.right, targetSum - val);
        return ret;
    }


    public int pathSum_v1(TreeNode root, int targetSum) {
        if (root == null) {
            return 0;
        }

        int ret = rootSum(root, targetSum); // 统计以当前root为起点的向下路径里，和等于targetSum的条数
        ret += pathSum_v1(root.left, targetSum);
        ret += pathSum_v1(root.right, targetSum);
        return ret;
    }

    public int pathSum_v2(TreeNode root, int targetSum) {

        Map<Long,Integer> prefix = new HashMap<Long,Integer>();
        prefix.put(0L,1);
        return dfs(root,prefix,0,targetSum);
    }

    public int dfs(TreeNode root, Map<Long, Integer> prefix, long curr, int targetSum) {
        if(root == null){
            return 0;
        }

        int ret = 0;
        curr += root.val;

        ret = prefix.getOrDefault(curr-targetSum,0);
        prefix.put(curr,prefix.getOrDefault(curr, 0) + 1);
        ret += dfs(root.left,prefix,curr,targetSum);
        ret += dfs(root.right,prefix,curr,targetSum);
        prefix.put(curr,prefix.getOrDefault(curr,0) - 1);

        return ret;
    }

    private TreeNode ans_lca;

    public Solution(){
        this.ans_lca = null;
    }

    private boolean dfs(TreeNode root, TreeNode p, TreeNode q) {
        if (root == null) return false;
        boolean lson = dfs(root.left, p, q); //
        boolean rson = dfs(root.right, p, q);
        if ((lson && rson) || (root.val == p.val || root.val == q.val)) { // x节点的子树是否包含p节点或q节点
            ans_lca = root; // (lson && rson)：x节点的左孩子和右孩子都包含p节点或者q节点   (root.val == p.val || root.val == q.val)： x节点恰好是p或者q节点
        }
        return lson || rson || (root.val == p.val || root.val == q.val);
    }

    public TreeNode lowestCommonAncestor_v1(TreeNode root, TreeNode p, TreeNode q) {
        this.dfs(root, p, q);
        return this.ans_lca;
    }

    /*
      方法二：存储父节点
    */
    Map<Integer, TreeNode> parent = new HashMap<Integer, TreeNode>();
    Set<Integer> visited = new HashSet<>();

    public void dfs_v1(TreeNode root){
        if(root.left != null){
            parent.put(root.left.val,root);
            dfs_v1(root.left);
        }

        if(root.right != null){
            parent.put(root.right.val,root);
            dfs_v1(root.right);
        }
    }

    public TreeNode lowestCommonAncestor_v2(TreeNode root, TreeNode p, TreeNode q) {
        dfs_v1(root);
        while(p != null){
            visited.add(p.val);
            p = parent.get(p.val);
        }
        while(q != null){
            if(visited.contains(q.val)){
                return q;
            }

            q = parent.get(q.val);
        }

        return null;
    }

    /*
    *  二叉树中的最大路径和 解法1 递归
    * */

    int maxSum = Integer.MIN_VALUE;

    public int maxPathSum(TreeNode root) {
       maxGain(root);
       return maxSum;
    }

    public int maxGain(TreeNode node){
        if(node == null){
            return 0;
        }

        int leftGain = Math.max(maxGain(node.left),0);
        int rightGain = Math.max(maxGain(node.right),0);

        int priceNewpath = node.val + leftGain + rightGain;

        maxSum = Math.max(maxSum,priceNewpath); // 路径三种情况： 左左 左中右 右右

        return node.val + Math.max(leftGain,rightGain);
    }

    /*
    * 岛屿数量
    * */

     void dfs(char[][] grid,int r,int c){
         int nr = grid.length;
         int nc = grid[0].length;

         if(r<0||c<0||r>=nr||c>=nc||grid[r][c] == '0') return;

         grid[r][c] = '0';
         dfs(grid,r-1,c);
         dfs(grid,r+1,c);
         dfs(grid,r,c-1);
         dfs(grid,r,c+1);
     }

    public int numIslands_v1(char[][] grid) {
        if(grid == null || grid.length == 0) return 0;

        int nr = grid.length;
        int nc = grid[0].length;
        int num_islands = 0;
        for(int r=0;r<nr;r++){
            for(int c=0;c<nc;c++){
                if(grid[r][c] == '1'){
                    num_islands++;
                    dfs(grid,r,c);
                }
            }
        }
        return num_islands;
    }

    public int numIslands_v2(char[][] grid) {
        if (grid == null || grid.length == 0) {
            return 0;
        }

        int nr = grid.length;
        int nc = grid[0].length;
        int num_islands = 0;

        for (int r = 0; r < nr; ++r) {
            for (int c = 0; c < nc; ++c) {
                if (grid[r][c] == '1') {
                    ++num_islands;
                    grid[r][c] = '0';
                    Queue<Integer> neighbors = new LinkedList<>();
                    neighbors.add(r * nc + c);
                    while (!neighbors.isEmpty()) {
                        int id = neighbors.remove();
                        int row = id / nc;
                        int col = id % nc;
                        if (row - 1 >= 0 && grid[row-1][col] == '1') {
                            neighbors.add((row-1) * nc + col);
                            grid[row-1][col] = '0';
                        }
                        if (row + 1 < nr && grid[row+1][col] == '1') {
                            neighbors.add((row+1) * nc + col);
                            grid[row+1][col] = '0';
                        }
                        if (col - 1 >= 0 && grid[row][col-1] == '1') {
                            neighbors.add(row * nc + col-1);
                            grid[row][col-1] = '0';
                        }
                        if (col + 1 < nc && grid[row][col+1] == '1') {
                            neighbors.add(row * nc + col+1);
                            grid[row][col+1] = '0';
                        }
                    }
                }
            }
        }

        return num_islands;
    }

    public int[] searchRange_v1(int[] nums, int target) {
        int n = nums.length;
        int low = 0;
        int high = n - 1;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            if (nums[mid] < target) {
                low = mid + 1;
            }
            else if (nums[mid] > target) {
                high = mid - 1;
            }
            else {
                return new int[]{low, high};
            }
        }

        return new int[]{low, high-1};
    }

    public int[] searchRange_v2(int[] nums, int target) {
        int leftIdx = binarySearch(nums,target,true); //找第一个大于等于target的下标
        int rightIdx = binarySearch(nums,target,false) - 1; // 找第一个大于target的下标再-1
        if(leftIdx <= rightIdx && rightIdx < nums.length && nums[leftIdx] == target && nums[rightIdx] == target) {
            return new int[]{leftIdx, rightIdx};
        }

        return new int[]{-1, -1};

    }

    public int binarySearch(int[] nums, int target,boolean lower) {
        int left = 0,right = nums.length-1,ans = nums.length;
        while(left <= right){
            int mid = (left + right)/2;
            if(nums[mid] > target || (lower && nums[mid] == target)){
                right = mid -1;
                ans = mid;
            }else{
                left = mid +1;
            }

        }
        return ans;
    }

    public int search(int[] nums, int target) {
         int n = nums.length;
         if(n == 0) return -1;
         if(n == 1) return nums[0] == target ? 0 : -1;

         int l =0,r=n-1;
         while(l <= r){
             int mid = (l + r)/2;
             if(nums[mid] == target){
                 return mid;
             }
             if(nums[0] <= nums[mid]){ // 旋转点不在[0,mid]区间
                 if(nums[0] <= target && target < nums[mid] ){ // target值在这个升序区间 and 目标值在[0,mid]区间
                     r = mid - 1;
                 }else{ // 可能在[mid,前有序终止点] or 在旋转之后的有序区间
                     l = mid +1;
                 }
             }else{
                 if (nums[mid] < target && target <= nums[n - 1]) {
                     l = mid + 1;
                 } else {
                     r = mid - 1;
                 }
             }
         }
         return -1;
    }

    public int findMin(int[] nums) {
         int low = 0;
         int high = nums.length-1;
         while(low < high){
             int pivot = low + (high - low)/2;
             if(nums[pivot] < nums[high]){
                 high = pivot;
             }else{
                 low = pivot + 1;
             }
         }
         return nums[low];
    }

    public double findMedianSortedArrays(int[] nums1, int[] nums2) {
         if(nums1.length > nums2.length){ // 保证元素多的哪一个数组始终是nums2
             return findMedianSortedArrays(nums2, nums1);
         }

         int m = nums1.length;
         int n = nums2.length;
         int left = 0,right = m;

         int median1 = 0,median2 = 0; // 合并后左半段的最大值 + 右半段的最小值

         while(left <= right){
             int i = (left + right)/2; // nums1左半段取的元素的个数
             int j = (m + n + 1)/2 - i; // nums2左半段取的元素的个数

             int nums_im1 = (i == 0 ? Integer.MIN_VALUE : nums1[i - 1]); // nums1左半最右一个，i==0时哨兵为MIN
             int nums_i = (i == m ? Integer.MAX_VALUE : nums1[i]);         // nums1右半最左一个，i==m时哨兵为MAX
             int nums_jm1 = (j == 0 ? Integer.MIN_VALUE : nums2[j - 1]); // nums2左半最右一个，j==0时哨兵为MIN
             int nums_j = (j == n ? Integer.MAX_VALUE : nums2[j]);        // nums2右半最左一个，j==n时哨兵为MAX

             if (nums_im1 <= nums_j) {
                 median1 = Math.max(nums_im1, nums_jm1);
                 median2 = Math.min(nums_i, nums_j);
                 left = i + 1;
             } else {
                 right = i - 1;
             }

         }
        return (m + n) % 2 == 0 ? (median1 + median2) / 2.0 : median1;
    }

    public boolean isValid(String s){
         int n = s.length();
         if(n % 2 == 1){
             return false;
         }

         Map<Character,Character> pairs = new HashMap<Character,Character>(){{
             put(')', '(');
             put(']', '[');
             put('}', '{');
         }};

        Deque<Character> stack = new LinkedList<Character>();
        for (int i = 0; i < n; i++) {
            char ch = s.charAt(i);
            if(pairs.containsKey(ch)){
                if(stack.isEmpty() || stack.peek() != pairs.get(ch)){
                    return false;
                }
                stack.pop();
            }else{
                stack.push(ch);
            }
        }
        return stack.isEmpty();
    }

    int ptr;
    public String docodeString(String s){
         LinkedList<String> stk = new LinkedList<>();
         ptr = 0;

         while(ptr < s.length()){
             char cur = s.charAt(ptr);
             if(Character.isDigit(cur)){
                 String digits = getDigits(s);
                 stk.addLast(digits);
             }else if(Character.isLetter(cur) || cur == '[') {
                 stk.addLast(String.valueOf(s.charAt(ptr++)));
             }else{
                 ++ptr;
                 LinkedList<String> sub = new LinkedList<>();
                 while(!"[".equals(stk.peekLast())){
                     sub.addLast(stk.removeLast());
                 }
                 Collections.reverse(sub);
                 stk.removeLast();
                 int repTime = Integer.parseInt(stk.removeLast());
                 StringBuffer t = new StringBuffer();
                 String o = getString(sub);

                 while(repTime-- > 0){
                     t.append(o);
                 }
                 stk.addLast(t.toString());
             }
         }
         return getString(stk);
    }

    public String getDigits(String s) {
        StringBuffer ret = new StringBuffer();
        while (Character.isDigit(s.charAt(ptr))) {
            ret.append(s.charAt(ptr++));
        }
        return ret.toString();
    }

    public String getString(LinkedList<String> v) {
        StringBuffer ret = new StringBuffer();
        for (String s : v) {
            ret.append(s);
        }
        return ret.toString();
    }

    public int[] dailyTemperatures(int[] temperatures) {
        int length = temperatures.length;
        int[] ans = new int[length];
        Deque<Integer> stack = new LinkedList<>();
        for(int i = 0; i < length; i++){
            int temperature = temperatures[i];
            while(!stack.isEmpty() && temperature > temperatures[stack.peek()]){
                int prevIndex = stack.pop();
                ans[prevIndex] =     - prevIndex;
            }
            stack.push(i);
        }
        return ans;
    }

    public int largestRectangleArea(int[] heights) {
        int n = heights.length;
        int[] left = new int[n];
        int[] right = new int[n];

        Deque<Integer> mono_stack = new ArrayDeque<>();
        for(int i = 0; i < n; i++){
            while(!mono_stack.isEmpty() && heights[mono_stack.peek()] >= heights[i]){
                mono_stack.pop();
            }
            left[i] = (mono_stack.isEmpty() ? -1 : mono_stack.peek());
            mono_stack.push(i);
        }

        mono_stack.clear();
        for(int i = n-1;i >= 0;--i){
            while(!mono_stack.isEmpty() && heights[mono_stack.peek()] >= heights[i]){
                mono_stack.pop();
            }
            right[i] = (mono_stack.isEmpty() ? n : mono_stack.peek());
            mono_stack.push(i);
        }

        int ans = 0;
        for (int i = 0; i < n; ++i) {
            ans = Math.max(ans, (right[i] - left[i] - 1) * heights[i]);
        }

        return ans;
    }



}




public class test {
    public static void main(String[] args) {
        Solution solution = new Solution();

        System.out.println(solution.largestRectangleArea(new int[]{2,1,5,6,2,3}));

//        System.out.println(Arrays.toString(solution.dailyTemperatures(new int[]{73,74,75,71,69,72,76,73}))); // [1, 1, 4, 2, 1, 1, 0, 0]

//        System.out.println(solution.docodeString("3[a2[c]]")); // accaccacc

//        System.out.println(solution.isValid("({[]})")); // true

//        System.out.println(solution.findMedianSortedArrays(new int[]{1, 3}, new int[]{2})); // 2.0

//        System.out.println(solution.search(new int[]{4, 5, 6, 7, 0, 1, 2}, 0)); // 4

//        System.out.println(Arrays.toString(solution.searchRange_v2(new int[]{5, 7, 7, 8, 8, 10}, 8))); // [3, 4]

//        TreeNode root = buildLevel(10, 5, -3, 3, 2, null, 11, 3, -2, null, 1);
//        System.out.println(solution.pathSum_v2(root, 8)); // 3

//        TreeNode lcaRoot = buildLevel(3, 5, 1, 6, 2, 0, 8, null, null, 7, 4);
//        TreeNode p = find(lcaRoot, 5);
//        TreeNode q = find(lcaRoot, 4);
//        System.out.println(solution.lowestCommonAncestor_v2(lcaRoot, p, q).val); // 5

//        TreeNode maxRoot = buildLevel(-10, 9, 20, null, null, 15, 7);
//        System.out.println(solution.maxPathSum(maxRoot)); // 42

//        char[][] grid = {
//            {'1','1','0','0','0'},
//            {'1','1','0','0','0'},
//            {'0','0','1','0','0'},
//            {'0','0','0','1','1'}
//        };
//        System.out.println(solution.numIslands_v2(grid)); // 3
    }

    static TreeNode buildLevel(Integer... vals) {
        if (vals == null || vals.length == 0 || vals[0] == null) return null;
        TreeNode[] nodes = new TreeNode[vals.length];
        for (int i = 0; i < vals.length; i++) {
            if (vals[i] != null) nodes[i] = new TreeNode(vals[i]);
        }
        for (int i = 0; i < vals.length; i++) {
            if (nodes[i] == null) continue;
            int l = 2 * i + 1, r = 2 * i + 2;
            if (l < vals.length) nodes[i].left = nodes[l];
            if (r < vals.length) nodes[i].right = nodes[r];
        }
        return nodes[0];
    }

    static TreeNode find(TreeNode n, int v) {
        if (n == null) return null;
        if (n.val == v) return n;
        TreeNode l = find(n.left, v);
        return l != null ? l : find(n.right, v);
    }
}