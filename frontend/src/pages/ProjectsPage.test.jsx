import { render,screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach,describe,expect,it,vi } from 'vitest'
import ProjectsPage from './ProjectsPage'

vi.mock('@/api',()=>({fetchProjects:vi.fn().mockResolvedValue({items:[],meta:{page:0,totalElements:0,totalPages:0}}),createProject:vi.fn().mockResolvedValue({})}))
describe('ProjectsPage',()=>{beforeEach(()=>vi.clearAllMocks());it('opens a controlled create form with detailed description',async()=>{render(<MemoryRouter><ProjectsPage/></MemoryRouter>);await userEvent.click(screen.getByRole('button',{name:'新建项目'}));const input=screen.getByLabelText(/项目名称/);await userEvent.type(input,'新项目');expect(input).toHaveValue('新项目');expect(screen.getByLabelText('项目详细描述')).toBeInTheDocument()})})
